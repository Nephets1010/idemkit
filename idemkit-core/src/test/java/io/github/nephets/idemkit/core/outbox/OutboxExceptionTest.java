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
package io.github.nephets.idemkit.core.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxExceptionTest {

    @Test
    void 属于RuntimeException且保留信息() {
        OutboxException ex = new OutboxException("投递失败");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("投递失败");
    }

    @Test
    void 可包装原因() {
        Throwable cause = new IllegalStateException("底层错误");
        assertThat(new OutboxException("投递失败", cause).getCause()).isSameAs(cause);
    }
}
