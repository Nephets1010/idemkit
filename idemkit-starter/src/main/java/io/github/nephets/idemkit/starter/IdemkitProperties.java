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

import org.springframework.boot.context.properties.ConfigurationProperties;

/** idemkit 的配置项，前缀 {@code idemkit}。 */
@ConfigurationProperties(prefix = "idemkit")
public class IdemkitProperties {

    /** 是否启用自动装配，默认启用。 */
    private boolean enabled = true;

    /** 携带租户号的请求头名称。 */
    private String tenantHeader = "X-Tenant-Id";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }
}
