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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void none_只尝试一次且不等待() {
        RetryPolicy p = RetryPolicy.none();
        assertThat(p.maxAttempts()).isEqualTo(1);
        assertThat(p.backoff()).isEqualTo(RetryPolicy.Backoff.NONE);
        assertThat(p.delayFor(0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void fixed_每次失败后等待相同间隔() {
        RetryPolicy p = RetryPolicy.fixed(3, Duration.ofMillis(50));
        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.delayFor(0)).isEqualTo(Duration.ofMillis(50));
        assertThat(p.delayFor(1)).isEqualTo(Duration.ofMillis(50));
        assertThat(p.delayFor(2)).isEqualTo(Duration.ZERO);
    }

    @Test
    void exponential_等待随失败次数翻倍并封顶() {
        RetryPolicy p = RetryPolicy.exponential(4, Duration.ofMillis(10), Duration.ofMillis(100));
        assertThat(p.delayFor(0)).isEqualTo(Duration.ofMillis(10));
        assertThat(p.delayFor(1)).isEqualTo(Duration.ofMillis(20));
        assertThat(p.delayFor(2)).isEqualTo(Duration.ofMillis(40));
    }

    @Test
    void exponential_超过上限后封顶() {
        RetryPolicy p = RetryPolicy.exponential(6, Duration.ofMillis(10), Duration.ofMillis(100));
        assertThat(p.delayFor(4)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void fixed_零次尝试抛异常() {
        assertThatThrownBy(() -> RetryPolicy.fixed(0, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponential_零次尝试抛异常() {
        assertThatThrownBy(
                        () ->
                                RetryPolicy.exponential(
                                        0, Duration.ofMillis(1), Duration.ofMillis(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sleepFor_零等待不抛异常() {
        RetryPolicy.none().sleepFor(0);
        RetryPolicy.fixed(3, Duration.ofMillis(1)).sleepFor(0);
    }
}
