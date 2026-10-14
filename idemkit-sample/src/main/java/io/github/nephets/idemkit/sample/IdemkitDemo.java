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
package io.github.nephets.idemkit.sample;

import io.github.nephets.idemkit.core.IdemkitTenant;
import io.github.nephets.idemkit.core.idempotency.IdempotencyKey;
import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.outbox.OutboxRecord;
import io.github.nephets.idemkit.core.outbox.OutboxStore;
import io.github.nephets.idemkit.core.saga.SagaContext;
import io.github.nephets.idemkit.core.saga.SagaExecutionException;
import io.github.nephets.idemkit.core.saga.SagaOrchestrator;
import io.github.nephets.idemkit.core.saga.SagaStep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 启动即跑一遍三个能力的典型用法，输出到日志，方便直接看效果。 */
@Component
public class IdemkitDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IdemkitDemo.class);

    private final IdempotentStore idempotentStore;
    private final OutboxStore outboxStore;

    public IdemkitDemo(IdempotentStore idempotentStore, OutboxStore outboxStore) {
        this.idempotentStore = idempotentStore;
        this.outboxStore = outboxStore;
    }

    @Override
    public void run(String... args) {
        demoIdempotency();
        demoOutbox();
        demoSaga();
    }

    private void demoIdempotency() {
        String key = "demo-order-1";
        String fingerprint = "amount=10";
        IdempotencyKey first = idempotentStore.getOrCreate(key, fingerprint);
        idempotentStore.complete(key, "paid");
        IdempotencyKey replay = idempotentStore.getOrCreate(key, fingerprint);
        log.info("[幂等] 首次={}，重放={}", first.status(), replay.status());
    }

    private void demoOutbox() {
        OutboxRecord record = outboxStore.append("order.created", "{\"id\":1}");
        outboxStore.markSent(record.id());
        log.info("[Outbox] 写入 {} 条待投递，标记后剩余 {}", 1, outboxStore.pending().size());
    }

    private void demoSaga() {
        List<String> logSteps = new ArrayList<>();
        SagaStep reserve = step("扣库存", logSteps, false);
        SagaStep pay = step("扣款", logSteps, false);
        SagaOrchestrator happy = SagaOrchestrator.builder().step(reserve).step(pay).build();
        happy.run(SagaContext.of(IdemkitTenant.of("demo")));
        log.info("[SAGA] 成功路径执行顺序：{}", logSteps);

        logSteps.clear();
        SagaStep reserve2 = step("扣库存", logSteps, false);
        SagaStep payFail =
                new SagaStep() {
                    public String name() {
                        return "扣款";
                    }

                    public void execute(SagaContext ctx) {
                        logSteps.add("exec:扣款");
                        throw new IllegalStateException("余额不足");
                    }

                    public void compensate(SagaContext ctx) {
                        logSteps.add("comp:扣款");
                    }
                };
        SagaOrchestrator fail = SagaOrchestrator.builder().step(reserve2).step(payFail).build();
        try {
            fail.run(SagaContext.of(IdemkitTenant.of("demo")));
        } catch (SagaExecutionException e) {
            log.info("[SAGA] 失败路径补偿顺序：{}（失败步骤 {}）", logSteps, e.failedStep());
        }
    }

    private SagaStep step(String name, List<String> log, boolean fail) {
        return new SagaStep() {
            public String name() {
                return name;
            }

            public void execute(SagaContext ctx) {
                log.add("exec:" + name);
                if (fail) {
                    throw new IllegalStateException(name + " 失败");
                }
            }

            public void compensate(SagaContext ctx) {
                log.add("comp:" + name);
            }
        };
    }
}
