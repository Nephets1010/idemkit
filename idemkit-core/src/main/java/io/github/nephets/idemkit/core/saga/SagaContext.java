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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** SAGA 一次运行贯穿全程的上下文。各步骤通过它共享数据，也通过它记录已经执行成功、需要逆序补偿的步骤。 */
public final class SagaContext {

    private final IdemkitTenant tenant;
    private final Map<String, Object> data = new LinkedHashMap<>();
    private final List<String> completedSteps = new ArrayList<>();

    public SagaContext(IdemkitTenant tenant) {
        this.tenant = Objects.requireNonNull(tenant, "tenant 不能为空");
    }

    public static SagaContext of(IdemkitTenant tenant) {
        return new SagaContext(tenant);
    }

    public IdemkitTenant tenant() {
        return tenant;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        Object v = data.get(key);
        if (v == null) {
            return null;
        }
        return type.cast(v);
    }

    /** 记录一个步骤执行成功（供失败时逆序补偿使用）。 */
    public void recordStep(String name) {
        completedSteps.add(name);
    }

    /** 已成功执行的步骤名，按执行顺序排列。 */
    public List<String> completedSteps() {
        return new ArrayList<>(completedSteps);
    }
}
