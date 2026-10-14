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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdemkitTenantTest {

    @Test
    void of_返回带租户号的实例() {
        IdemkitTenant tenant = IdemkitTenant.of("acme");
        assertThat(tenant.tenantId()).isEqualTo("acme");
    }

    @Test
    void tenantId_等于构造时传入的值() {
        assertThat(IdemkitTenant.of("shop").tenantId()).isEqualTo("shop");
    }

    @Test
    void equals_相同租户号视为相等() {
        assertThat(IdemkitTenant.of("acme")).isEqualTo(IdemkitTenant.of("acme"));
    }

    @Test
    void equals_不同租户号不相等() {
        assertThat(IdemkitTenant.of("acme")).isNotEqualTo(IdemkitTenant.of("other"));
    }

    @Test
    void hashCode_相同租户号一致() {
        assertThat(IdemkitTenant.of("acme").hashCode())
                .isEqualTo(IdemkitTenant.of("acme").hashCode());
    }

    @Test
    void of_传入空字符串抛异常() {
        assertThatThrownBy(() -> IdemkitTenant.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toString_包含租户号() {
        assertThat(IdemkitTenant.of("acme").toString()).contains("acme");
    }
}
