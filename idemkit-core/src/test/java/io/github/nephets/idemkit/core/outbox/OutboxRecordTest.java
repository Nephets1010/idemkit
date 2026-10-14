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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRecordTest {

    @Test
    void builder_默认处于待投递() {
        OutboxRecord r = OutboxRecord.builder("id1", "order.created", "{}").build();
        assertThat(r.id()).isEqualTo("id1");
        assertThat(r.topic()).isEqualTo("order.created");
        assertThat(r.payload()).isEqualTo("{}");
        assertThat(r.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(r.sentAt()).isNull();
    }

    @Test
    void sent_翻转状态并记录时间() {
        OutboxRecord r = OutboxRecord.builder("id1", "t", "p").build().sent();
        assertThat(r.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(r.sentAt()).isNotNull();
    }

    @Test
    void sent_不修改原对象() {
        OutboxRecord original = OutboxRecord.builder("id1", "t", "p").build();
        original.sent();
        assertThat(original.status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void equals_只看id() {
        OutboxRecord a = OutboxRecord.builder("id1", "t", "p").build();
        OutboxRecord b = OutboxRecord.builder("id1", "other", "x").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void builder_可显式指定时间() {
        Instant t = Instant.parse("2026-10-11T00:00:00Z");
        OutboxRecord r =
                OutboxRecord.builder("id1", "t", "p").status(OutboxStatus.SENT).sentAt(t).build();
        assertThat(r.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(r.sentAt()).isEqualTo(t);
    }
}
