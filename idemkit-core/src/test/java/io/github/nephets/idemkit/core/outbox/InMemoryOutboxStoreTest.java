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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryOutboxStoreTest {

    private final InMemoryOutboxStore store = new InMemoryOutboxStore();

    @Test
    void append_返回待投递且带有生成id() {
        OutboxRecord r = store.append("order.created", "{}");
        assertThat(r.id()).isNotEmpty();
        assertThat(r.status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void pending_只返回尚未投递的() {
        OutboxRecord a = store.append("t", "1");
        OutboxRecord b = store.append("t", "2");
        store.markSent(a.id());
        List<OutboxRecord> pending = store.pending();
        assertThat(pending).extracting(OutboxRecord::id).containsExactly(b.id());
    }

    @Test
    void pending_按写入时间升序() throws Exception {
        OutboxRecord a = store.append("t", "1");
        Thread.sleep(5);
        OutboxRecord b = store.append("t", "2");
        Thread.sleep(5);
        OutboxRecord c = store.append("t", "3");
        List<OutboxRecord> pending = store.pending();
        assertThat(pending).extracting(OutboxRecord::id).containsExactly(a.id(), b.id(), c.id());
    }

    @Test
    void markSent_翻转为已投递() {
        OutboxRecord r = store.append("t", "1");
        store.markSent(r.id());
        OutboxRecord after = store.pending().stream().findFirst().orElse(null);
        assertThat(after).isNull();
        assertThat(store.append("t", "2")).isNotNull();
    }

    @Test
    void markSent_记录不存在抛异常() {
        assertThatThrownBy(() -> store.markSent("missing"))
                .isInstanceOf(OutboxException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void append_多次写入互不影响() {
        store.append("t", "1");
        store.append("t", "2");
        assertThat(store.pending()).hasSize(2);
    }

    @Test
    void markSent_重复投递幂等() {
        OutboxRecord r = store.append("t", "1");
        store.markSent(r.id());
        store.markSent(r.id());
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void 构造函数拒绝非正容量() {
        assertThatThrownBy(() -> new InMemoryOutboxStore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryOutboxStore(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markSent_已发送消息不再出现在pending() {
        OutboxRecord a = store.append("t", "1");
        OutboxRecord b = store.append("t", "2");
        store.markSent(a.id());
        List<OutboxRecord> pending = store.pending();
        assertThat(pending).extracting(OutboxRecord::id).doesNotContain(a.id());
        assertThat(pending).extracting(OutboxRecord::id).containsExactly(b.id());
    }

    @Test
    void markSent_从pending移除已发送消息防止无限增长() {
        for (int i = 0; i < 50; i++) {
            OutboxRecord r = store.append("t", String.valueOf(i));
            store.markSent(r.id());
        }
        // 全部已发送，未投递集合应为空
        assertThat(store.pending()).isEmpty();
        // 已发送消息不在 pending 中返回
        OutboxRecord extra = store.append("t", "extra");
        assertThat(store.pending()).extracting(OutboxRecord::id).containsExactly(extra.id());
    }

    @Test
    void 已发送日志超过容量按LRU淘汰且pending始终可查询() {
        int sentCapacity = 10;
        InMemoryOutboxStore bounded = new InMemoryOutboxStore(sentCapacity);
        for (int i = 0; i < 30; i++) {
            OutboxRecord r = bounded.append("t", String.valueOf(i));
            bounded.markSent(r.id());
        }
        // 已发送日志有界
        assertThat(bounded.sentSize()).isLessThanOrEqualTo(sentCapacity);
        // 待投递集合为空（所有消息都已发送）
        assertThat(bounded.pending()).isEmpty();
        // 新追加的未发送消息依然可查询（pending 永不淘汰）
        OutboxRecord fresh = bounded.append("t", "fresh");
        assertThat(bounded.pending()).extracting(OutboxRecord::id).containsExactly(fresh.id());
    }
}
