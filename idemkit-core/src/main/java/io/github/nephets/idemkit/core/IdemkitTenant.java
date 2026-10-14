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

import java.util.Objects;

/** 租户标识。幂等键、Outbox 与 SAGA 运行都按租户隔离，避免不同业务共用同一命名空间。 */
public final class IdemkitTenant {

    private final String tenantId;

    private IdemkitTenant(String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId 不能为空");
    }

    public static IdemkitTenant of(String tenantId) {
        return new IdemkitTenant(tenantId);
    }

    public String tenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdemkitTenant)) {
            return false;
        }
        IdemkitTenant that = (IdemkitTenant) o;
        return tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        return tenantId.hashCode();
    }

    @Override
    public String toString() {
        return "IdemkitTenant{tenantId='" + tenantId + "'}";
    }
}
