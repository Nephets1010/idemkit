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

import io.github.nephets.idemkit.core.IdemkitTenant;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaOrchestratorTest {

    private final IdemkitTenant tenant = IdemkitTenant.of("acme");

    /** 记录执行与补偿顺序的步骤；可设置前若干次执行失败。 */
    static class Step implements SagaStep {
        final String name;
        final List<String> log;
        final AtomicInteger failRemaining;
        final RuntimeException compensateError;
        int executeCount;
        int compensateCount;

        Step(String name, List<String> log) {
            this(name, log, 0, null);
        }

        Step(String name, List<String> log, int failRemaining, RuntimeException compensateError) {
            this.name = name;
            this.log = log;
            this.failRemaining = new AtomicInteger(failRemaining);
            this.compensateError = compensateError;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void execute(SagaContext ctx) throws Exception {
            executeCount++;
            log.add("exec:" + name);
            if (failRemaining.getAndDecrement() > 0) {
                throw new IllegalStateException(name + " 执行失败");
            }
        }

        @Override
        public void compensate(SagaContext ctx) throws Exception {
            compensateCount++;
            log.add("comp:" + name);
            if (compensateError != null) {
                throw compensateError;
            }
        }
    }

    @Test
    void 全部成功_按序执行且返回成功结果() {
        List<String> log = new ArrayList<>();
        Step a = new Step("A", log);
        Step b = new Step("B", log);
        Step c = new Step("C", log);
        SagaOutcome o =
                SagaOrchestrator.builder()
                        .step(a)
                        .step(b)
                        .step(c)
                        .build()
                        .run(SagaContext.of(tenant));
        assertThat(o.success()).isTrue();
        assertThat(o.completedSteps()).containsExactly("A", "B", "C");
        assertThat(log).containsExactly("exec:A", "exec:B", "exec:C");
        assertThat(a.compensateCount).isZero();
    }

    @Test
    void 上下文数据在步骤间共享() {
        List<String> log = new ArrayList<>();
        Step a =
                new Step("A", log) {
                    @Override
                    public void execute(SagaContext ctx) throws Exception {
                        super.execute(ctx);
                        ctx.put("value", 42);
                    }
                };
        Step b =
                new Step("B", log) {
                    @Override
                    public void execute(SagaContext ctx) throws Exception {
                        super.execute(ctx);
                        ctx.put("doubled", ctx.<Integer>get("value") * 2);
                    }
                };
        SagaContext ctx = SagaContext.of(tenant);
        SagaOrchestrator.builder().step(a).step(b).build().run(ctx);
        assertThat(ctx.<Integer>get("value")).isEqualTo(42);
    }

    @Test
    void 某步失败_已完成步骤逆序补偿并抛执行异常() {
        List<String> log = new ArrayList<>();
        Step a = new Step("A", log);
        Step b = new Step("B", log);
        Step c = new Step("C", log, 99, null);
        SagaOrchestrator orch = SagaOrchestrator.builder().step(a).step(b).step(c).build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaExecutionException.class)
                .hasFieldOrPropertyWithValue("failedStep", "C");
        assertThat(log).containsExactly("exec:A", "exec:B", "exec:C", "comp:B", "comp:A");
        assertThat(a.compensateCount).isEqualTo(1);
        assertThat(b.compensateCount).isEqualTo(1);
        assertThat(c.compensateCount).isZero();
    }

    @Test
    void 重试成功_只执行两次且不补偿() {
        List<String> log = new ArrayList<>();
        Step flaky = new Step("F", log, 1, null);
        SagaOrchestrator orch =
                SagaOrchestrator.builder()
                        .step(flaky)
                        .retryPolicy(RetryPolicy.fixed(3, Duration.ofMillis(1)))
                        .build();
        SagaOutcome o = orch.run(SagaContext.of(tenant));
        assertThat(o.success()).isTrue();
        assertThat(flaky.executeCount).isEqualTo(2);
        assertThat(flaky.compensateCount).isZero();
    }

    @Test
    void 重试耗尽_仍抛执行异常并补偿前序() {
        List<String> log = new ArrayList<>();
        Step ok = new Step("A", log);
        Step bad = new Step("B", log, 99, null);
        SagaOrchestrator orch =
                SagaOrchestrator.builder()
                        .step(ok)
                        .step(bad)
                        .retryPolicy(RetryPolicy.fixed(3, Duration.ofMillis(1)))
                        .build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaExecutionException.class)
                .hasFieldOrPropertyWithValue("failedStep", "B");
        assertThat(bad.executeCount).isEqualTo(3);
        assertThat(log).endsWith("comp:A");
    }

    @Test
    void 无重试策略_失败步骤只执行一次() {
        List<String> log = new ArrayList<>();
        Step bad = new Step("B", log, 99, null);
        SagaOrchestrator orch = SagaOrchestrator.builder().step(bad).build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaExecutionException.class);
        assertThat(bad.executeCount).isEqualTo(1);
    }

    @Test
    void 补偿本身失败_抛补偿异常() {
        List<String> log = new ArrayList<>();
        Step a = new Step("A", log, 0, new IllegalStateException("A 回滚失败"));
        Step b = new Step("B", log, 99, null);
        SagaOrchestrator orch = SagaOrchestrator.builder().step(a).step(b).build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaCompensationException.class)
                .hasFieldOrPropertyWithValue("failedCompensations", List.of("A: A 回滚失败"));
    }

    /** 永远失败的步骤；异常消息带线程名，用于断言失败原因的归属。 */
    static class FailingStep implements SagaStep {

        @Override
        public String name() {
            return "B";
        }

        @Override
        public void execute(SagaContext ctx) {
            throw new IllegalStateException("boom-" + Thread.currentThread().getName());
        }

        @Override
        public void compensate(SagaContext ctx) {
            // 失败步骤自身不参与补偿
        }
    }

    @Test
    void 空编排器构建时抛异常() {
        assertThatThrownBy(() -> SagaOrchestrator.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_空上下文抛异常() {
        SagaOrchestrator orch =
                SagaOrchestrator.builder().step(new Step("A", new ArrayList<>())).build();
        assertThatThrownBy(() -> orch.run(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 同一编排器并发执行_异常原因互不串写() throws Exception {
        // 回归：失败原因曾以实例字段 lastError 在 tryExecute 与 run 之间传递，
        // 多个线程并发 run 同一编排器时会互相覆盖，SagaExecutionException 可能携带
        // 其他 saga 的 cause。修复后失败原因是每次 run 的本地状态。
        List<String> log = new CopyOnWriteArrayList<>();
        Step a =
                new Step("A", log) {
                    @Override
                    public void compensate(SagaContext ctx) throws Exception {
                        Thread.sleep(2); // 拉长「记录失败 → 抛出」窗口，放大并发串写概率
                        super.compensate(ctx);
                    }
                };
        SagaOrchestrator orch =
                SagaOrchestrator.builder()
                        .step(a)
                        .step(new FailingStep())
                        .retryPolicy(RetryPolicy.fixed(2, Duration.ofMillis(1)))
                        .build();

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String[]>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    String self = Thread.currentThread().getName();
                                    try {
                                        orch.run(SagaContext.of(tenant));
                                        return new String[] {self, null};
                                    } catch (SagaExecutionException e) {
                                        return new String[] {
                                            self,
                                            e.getCause() == null ? null : e.getCause().getMessage()
                                        };
                                    }
                                }));
            }
            for (Future<String[]> future : futures) {
                String[] result = future.get(10, TimeUnit.SECONDS);
                assertThat(result[1]).isNotNull(); // 每个 saga 都必须失败
                assertThat(result[1]).contains(result[0]); // 携带的是本线程的失败原因
            }
        } finally {
            pool.shutdown();
        }
    }
}
