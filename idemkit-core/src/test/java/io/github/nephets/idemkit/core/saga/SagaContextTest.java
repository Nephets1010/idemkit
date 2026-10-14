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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaContextTest {

    @Test
    void of_携带租户() {
        SagaContext ctx = SagaContext.of(IdemkitTenant.of("acme"));
        assertThat(ctx.tenant().tenantId()).isEqualTo("acme");
    }

    @Test
    void put与get_能回取数据() {
        SagaContext ctx = SagaContext.of(IdemkitTenant.of("acme"));
        ctx.put("orderId", 123L);
        assertThat(ctx.<Long>get("orderId")).isEqualTo(123L);
        assertThat(ctx.get("orderId", Long.class)).isEqualTo(123L);
    }

    @Test
    void recordStep_按执行顺序记录() {
        SagaContext ctx = SagaContext.of(IdemkitTenant.of("acme"));
        ctx.recordStep("A");
        ctx.recordStep("B");
        assertThat(ctx.completedSteps()).containsExactly("A", "B");
    }

    @Test
    void completedSteps_返回副本不被外部修改影响() {
        SagaContext ctx = SagaContext.of(IdemkitTenant.of("acme"));
        ctx.recordStep("A");
        ctx.completedSteps().add("X");
        assertThat(ctx.completedSteps()).containsExactly("A");
    }

    @Test
    void of_空租户抛异常() {
        assertThatThrownBy(() -> SagaContext.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void get_缺失返回null() {
        SagaContext ctx = SagaContext.of(IdemkitTenant.of("acme"));
        String missing = ctx.get("missing");
        assertThat(missing).isNull();
        String missingTyped = ctx.get("missing", String.class);
        assertThat(missingTyped).isNull();
    }
}
