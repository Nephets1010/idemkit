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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyTest {

    @Test
    void pending_新建记录处于进行中且未结束() {
        IdempotencyKey key = IdempotencyKey.pending("k1", "fp-a");
        assertThat(key.key()).isEqualTo("k1");
        assertThat(key.fingerprint()).isEqualTo("fp-a");
        assertThat(key.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(key.isDone()).isFalse();
        assertThat(key.result()).isNull();
    }

    @Test
    void completed_进入成功态并保留结果() {
        IdempotencyKey done = IdempotencyKey.pending("k1", "fp-a").completed("{\"ok\":true}");
        assertThat(done.status()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(done.result()).isEqualTo("{\"ok\":true}");
        assertThat(done.isDone()).isTrue();
        assertThat(done.completedAt()).isNotNull();
    }

    @Test
    void failed_进入失败态并保留错误() {
        IdempotencyKey failed = IdempotencyKey.pending("k1", "fp-a").failed("timeout");
        assertThat(failed.status()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(failed.error()).isEqualTo("timeout");
        assertThat(failed.isDone()).isTrue();
    }

    @Test
    void completed_不修改原对象() {
        IdempotencyKey original = IdempotencyKey.pending("k1", "fp-a");
        original.completed("r");
        assertThat(original.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }

    @Test
    void equals_只看key() {
        IdempotencyKey a = IdempotencyKey.pending("k1", "fp-a");
        IdempotencyKey b = IdempotencyKey.pending("k1", "fp-b");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void builder_可显式指定状态与时间() {
        Instant now = Instant.parse("2026-10-11T00:00:00Z");
        IdempotencyKey key =
                IdempotencyKey.builder("k1", "fp-a")
                        .status(IdempotencyStatus.SUCCESS)
                        .result("r")
                        .createdAt(now)
                        .completedAt(now)
                        .build();
        assertThat(key.status()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(key.createdAt()).isEqualTo(now);
        assertThat(key.completedAt()).isEqualTo(now);
    }
}
