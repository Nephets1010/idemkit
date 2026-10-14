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

class SagaOutcomeTest {

    @Test
    void success_标记成功并带步骤名() {
        SagaOutcome o = SagaOutcome.success(List.of("A", "B"));
        assertThat(o.success()).isTrue();
        assertThat(o.completedSteps()).containsExactly("A", "B");
        assertThat(o.failedStep()).isNull();
    }

    @Test
    void builder_可填入失败步骤与错误() {
        SagaOutcome o =
                SagaOutcome.builder(false)
                        .failedStep("B")
                        .error("boom")
                        .completedSteps(List.of("A"))
                        .build();
        assertThat(o.success()).isFalse();
        assertThat(o.failedStep()).isEqualTo("B");
        assertThat(o.error()).isEqualTo("boom");
    }

    @Test
    void completedSteps_返回副本() {
        SagaOutcome o = SagaOutcome.success(List.of("A"));
        o.completedSteps().add("X");
        assertThat(o.completedSteps()).containsExactly("A");
    }
}
