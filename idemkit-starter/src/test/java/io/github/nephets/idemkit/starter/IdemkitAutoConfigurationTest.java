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
package io.github.nephets.idemkit.starter;

import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.outbox.OutboxStore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IdemkitAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(IdemkitAutoConfiguration.class));

    @Test
    void 默认注册核心Bean() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(IdempotentStore.class);
                    assertThat(ctx).hasSingleBean(OutboxStore.class);
                    assertThat(ctx).hasSingleBean(IdemkitController.class);
                    assertThat(ctx).hasSingleBean(IdemkitTenantFilter.class);
                });
    }

    @Test
    void 关闭时不注册幂等与Outbox() {
        runner.withPropertyValues("idemkit.enabled=false")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(IdempotentStore.class);
                            assertThat(ctx).doesNotHaveBean(OutboxStore.class);
                        });
    }

    @Test
    void 用户提供了存储实现时不再重复创建() {
        runner.withBean(
                        IdempotentStore.class,
                        io.github.nephets.idemkit.core.idempotency.InMemoryIdempotentStore::new)
                .run(ctx -> assertThat(ctx).hasSingleBean(IdempotentStore.class));
    }
}
