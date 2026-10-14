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
package io.github.nephets.idemkit.core.idempotency;

import java.util.Optional;

/**
 * 幂等键的存储抽象。可插拔实现（内存、Redis、数据库），核心只依赖这个接口。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>{@link #getOrCreate} 在键不存在时登记一条进行中记录；已存在且指纹一致时返回已有记录；指纹不一致抛 {@link
 *       IdempotencyConflictException}。
 *   <li>{@link #complete} / {@link #fail} 在键不存在时抛 {@link IllegalStateException}。
 * </ul>
 */
public interface IdempotentStore {

    /** 取得或登记一条进行中的幂等键。 */
    IdempotencyKey getOrCreate(String key, String fingerprint);

    /** 按 key 查找，不存在返回空。 */
    Optional<IdempotencyKey> find(String key);

    /** 标记成功并记录结果。 */
    void complete(String key, String result);

    /** 标记失败并记录错误信息。 */
    void fail(String key, String error);

    /** 是否已经处于终态（成功或失败）。 */
    boolean isDone(String key);
}
