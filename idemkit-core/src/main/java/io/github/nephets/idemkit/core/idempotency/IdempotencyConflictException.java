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

/** 同一个幂等键再次出现，但请求指纹与已登记的记录不一致时抛出。 */
public class IdempotencyConflictException extends RuntimeException {

    private final String key;

    public IdempotencyConflictException(String key, String message) {
        super(message);
        this.key = key;
    }

    public String key() {
        return key;
    }
}
