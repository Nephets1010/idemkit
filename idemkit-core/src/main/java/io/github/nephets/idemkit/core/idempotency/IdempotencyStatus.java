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

/** 幂等键在处理过程中的状态。 */
public enum IdempotencyStatus {
    /** 已登记，正在执行对应操作。 */
    IN_PROGRESS,
    /** 操作执行成功，可返回缓存结果。 */
    SUCCESS,
    /** 操作执行失败。 */
    FAILED
}
