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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SagaCompensationExceptionTest {

    @Test
    void 保留所有补偿失败的步骤() {
        SagaCompensationException ex = new SagaCompensationException(List.of("A: 回滚失败", "C: 超时"));
        assertThat(ex.failedCompensations()).containsExactly("A: 回滚失败", "C: 超时");
        assertThat(ex.getMessage()).contains("A: 回滚失败").contains("C: 超时");
    }
}
