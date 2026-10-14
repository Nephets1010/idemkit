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

/** 在一次请求内临时保存租户号，供控制器读取。 */
public final class IdemkitTenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private IdemkitTenantContext() {}

    public static void set(String tenant) {
        TENANT.set(tenant);
    }

    public static String get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
