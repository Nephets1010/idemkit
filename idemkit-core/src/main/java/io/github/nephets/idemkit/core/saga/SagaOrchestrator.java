/*
 * Copyright 2026 The idemkit Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.idemkit.core.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SAGA 编排器：按注册顺序正向执行步骤；某步在重试耗尽后仍失败，则对已完成的步骤做逆序补偿，并抛出 {@link SagaExecutionException}。若补偿本身也失败，则抛出
 * {@link SagaCompensationException}。
 */
public final class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final List<SagaStep> steps;
    private final RetryPolicy retryPolicy;

    private SagaOrchestrator(Builder b) {
        this.steps = new ArrayList<>(b.steps);
        this.retryPolicy = b.retryPolicy;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 按注册顺序执行全部步骤。全部成功返回结果；任一步失败（含重试）则触发补偿并抛异常。 */
    public SagaOutcome run(SagaContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为空");
        log.info("SAGA 开始执行，共 {} 个步骤，租户={}", steps.size(), ctx.tenant());
        List<SagaStep> executed = new ArrayList<>();
        for (SagaStep step : steps) {
            log.debug("SAGA 执行步骤 name={} attempt 上限={}", step.name(), retryPolicy.maxAttempts());
            Throwable failure = tryExecute(step, ctx);
            if (failure == null) {
                ctx.recordStep(step.name());
                executed.add(step);
                continue;
            }
            log.error("SAGA 步骤失败，触发逆序补偿 name={}", step.name(), failure);
            compensateReverse(executed, ctx);
            throw new SagaExecutionException(step.name(), failure);
        }
        log.info("SAGA 执行成功，已完成步骤={}", ctx.completedSteps());
        return SagaOutcome.success(ctx.completedSteps());
    }

    /**
     * 执行单个步骤（含重试）。
     *
     * <p>失败原因经返回值带回，不写实例字段：同一编排器可能被多个线程并发 {@link #run(SagaContext)}， 实例字段会在多次 saga 之间互相覆盖，使 {@link
     * SagaExecutionException} 携带其他 saga 的失败原因。
     *
     * @return 成功返回 {@code null}；重试耗尽返回最后一次异常（maxAttempts 为 0 时返回占位异常）
     */
    private Throwable tryExecute(SagaStep step, SagaContext ctx) {
        int max = retryPolicy.maxAttempts();
        Throwable lastFailure = new RuntimeException("未知错误");
        for (int attempt = 0; attempt < max; attempt++) {
            try {
                step.execute(ctx);
                return null;
            } catch (Exception e) {
                lastFailure = e;
                log.warn("SAGA 步骤执行异常 name={} attempt={}/{}", step.name(), attempt + 1, max, e);
                if (attempt < max - 1) {
                    retryPolicy.sleepFor(attempt);
                }
            }
        }
        return lastFailure;
    }

    private void compensateReverse(List<SagaStep> executed, SagaContext ctx) {
        List<String> failures = new ArrayList<>();
        for (int i = executed.size() - 1; i >= 0; i--) {
            SagaStep step = executed.get(i);
            try {
                step.compensate(ctx);
                log.debug("SAGA 补偿成功 name={}", step.name());
            } catch (Exception e) {
                log.error("SAGA 补偿失败 name={}", step.name(), e);
                failures.add(step.name() + ": " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new SagaCompensationException(failures);
        }
    }

    /** 构造器。 */
    public static final class Builder {
        private final List<SagaStep> steps = new ArrayList<>();
        private RetryPolicy retryPolicy = RetryPolicy.none();

        public Builder step(SagaStep step) {
            steps.add(Objects.requireNonNull(step, "step 不能为空"));
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy 不能为空");
            return this;
        }

        public SagaOrchestrator build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("SAGA 至少需要一个步骤");
            }
            return new SagaOrchestrator(this);
        }
    }
}
