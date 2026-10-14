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
package io.github.nephets.idemkit.core;

import io.github.nephets.idemkit.core.idempotency.IdempotencyConflictException;
import io.github.nephets.idemkit.core.idempotency.IdempotencyKey;
import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.idempotency.InMemoryIdempotentStore;
import io.github.nephets.idemkit.core.outbox.InMemoryOutboxStore;
import io.github.nephets.idemkit.core.outbox.OutboxRecord;
import io.github.nephets.idemkit.core.outbox.OutboxStore;
import io.github.nephets.idemkit.core.saga.SagaCompensationException;
import io.github.nephets.idemkit.core.saga.SagaContext;
import io.github.nephets.idemkit.core.saga.SagaExecutionException;
import io.github.nephets.idemkit.core.saga.SagaOrchestrator;
import io.github.nephets.idemkit.core.saga.SagaStep;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 把幂等、Outbox、SAGA 三个能力串起来跑一遍，验证它们在真实组合下行为正确。作为集成测试由 failsafe 执行。 */
class IdemkitFlowIT {

    private final IdemkitTenant tenant = IdemkitTenant.of("acme");

    @Test
    void 幂等重放_相同指纹只执行一次() {
        IdempotentStore store = new InMemoryIdempotentStore();
        List<String> executions = new ArrayList<>();
        String key = "pay-1001";
        String fingerprint = "amount=10";
        for (int i = 0; i < 3; i++) {
            IdempotencyKey existing = store.getOrCreate(key, fingerprint);
            if (existing.isDone()) {
                executions.add("replay:" + existing.result());
            } else {
                executions.add("exec");
                store.complete(key, "ok");
            }
        }
        assertThat(executions).containsExactly("exec", "replay:ok", "replay:ok");
    }

    @Test
    void 幂等冲突_不同指纹拒绝重放() {
        IdempotentStore store = new InMemoryIdempotentStore();
        store.getOrCreate("pay-1001", "amount=10");
        store.complete("pay-1001", "ok");
        assertThatThrownBy(() -> store.getOrCreate("pay-1001", "amount=20"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void outbox_写入后扫描并标记投递() {
        OutboxStore outbox = new InMemoryOutboxStore();
        OutboxRecord r = outbox.append("order.created", "{\"id\":1}");
        assertThat(outbox.pending()).hasSize(1);
        outbox.markSent(r.id());
        assertThat(outbox.pending()).isEmpty();
    }

    @Test
    void saga_失败触发逆序补偿() {
        List<String> log = new ArrayList<>();
        SagaStep reserve =
                new SagaStep() {
                    public String name() {
                        return "扣库存";
                    }

                    public void execute(SagaContext ctx) {
                        log.add("exec:扣库存");
                    }

                    public void compensate(SagaContext ctx) {
                        log.add("comp:扣库存");
                    }
                };
        SagaStep pay =
                new SagaStep() {
                    public String name() {
                        return "扣款";
                    }

                    public void execute(SagaContext ctx) {
                        log.add("exec:扣款");
                        throw new IllegalStateException("余额不足");
                    }

                    public void compensate(SagaContext ctx) {
                        log.add("comp:扣款");
                    }
                };
        SagaOrchestrator orch = SagaOrchestrator.builder().step(reserve).step(pay).build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaExecutionException.class);
        assertThat(log).containsExactly("exec:扣库存", "exec:扣款", "comp:扣库存");
    }

    @Test
    void saga_补偿失败抛出补偿异常() {
        List<String> log = new ArrayList<>();
        SagaStep badComp =
                new SagaStep() {
                    public String name() {
                        return "扣库存";
                    }

                    public void execute(SagaContext ctx) {
                        log.add("exec");
                    }

                    public void compensate(SagaContext ctx) {
                        throw new IllegalStateException("回滚失败");
                    }
                };
        SagaStep fail =
                new SagaStep() {
                    public String name() {
                        return "扣款";
                    }

                    public void execute(SagaContext ctx) {
                        throw new IllegalStateException("余额不足");
                    }

                    public void compensate(SagaContext ctx) {}
                };
        SagaOrchestrator orch = SagaOrchestrator.builder().step(badComp).step(fail).build();
        assertThatThrownBy(() -> orch.run(SagaContext.of(tenant)))
                .isInstanceOf(SagaCompensationException.class);
    }
}
