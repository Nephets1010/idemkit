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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStatusTest {

    @Test
    void values_包含三种状态() {
        assertThat(IdempotencyStatus.values())
                .containsExactlyInAnyOrder(
                        IdempotencyStatus.IN_PROGRESS,
                        IdempotencyStatus.SUCCESS,
                        IdempotencyStatus.FAILED);
    }

    @Test
    void valueOf_能按名字解析() {
        assertThat(IdempotencyStatus.valueOf("SUCCESS")).isEqualTo(IdempotencyStatus.SUCCESS);
    }
}
