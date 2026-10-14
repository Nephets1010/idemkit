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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdemkitPropertiesTest {

    @Test
    void 默认值_启用且头为X_Tenant_Id() {
        IdemkitProperties p = new IdemkitProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getTenantHeader()).isEqualTo("X-Tenant-Id");
    }

    @Test
    void setter_可关闭与改头() {
        IdemkitProperties p = new IdemkitProperties();
        p.setEnabled(false);
        p.setTenantHeader("X-Org");
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTenantHeader()).isEqualTo("X-Org");
    }
}
