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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 内存版 Outbox，单进程内可用，便于单元测试。
 *
 * <p>设计要点（堵住“已发送消息永不删除、无限增长”的 OOM 风险）：
 *
 * <ul>
 *   <li>尚未投递的 {@link OutboxStatus#PENDING} 消息存放在 {@code pending} 中，必须保持可查询，<b>不</b>做淘汰，否则会丢消息。
 *   <li>消息被 {@link #markSent(String)} 标记投递后，立即从 {@code pending} 中移除，因此 {@link #pending()}
 *       永远只返回未投递消息。
 *   <li>被投递的消息仅保留在一个<b>有容量上限</b>的已发送日志 {@code sentLog}（LRU 淘汰）里，仅用于 {@code markSent} 的幂等去重，避免无限增长。
 * </ul>
 */
public final class InMemoryOutboxStore implements OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryOutboxStore.class);

    /** 已发送日志的默认容量上限：100 万条，超过后按 LRU 淘汰最旧记录。 */
    public static final int DEFAULT_SENT_CAPACITY = 1_000_000;

    /** 尚未投递、必须保持可查询的消息。这部分不能淘汰，否则会丢消息。 */
    private final ConcurrentMap<String, OutboxRecord> pending = new ConcurrentHashMap<>();

    /** 已投递消息的边界日志：仅用于 markSent 幂等去重，容量有上限，超出后按 LRU 淘汰最旧记录。 */
    private final Map<String, OutboxRecord> sentLog;

    private volatile int sentCapacity;

    public InMemoryOutboxStore() {
        this(DEFAULT_SENT_CAPACITY);
    }

    /**
     * 指定已发送日志容量上限构造。
     *
     * @param sentCapacity 已发送日志容量上限，必须为正数
     */
    public InMemoryOutboxStore(int sentCapacity) {
        if (sentCapacity <= 0) {
            throw new IllegalArgumentException("sentCapacity 必须为正数: " + sentCapacity);
        }
        this.sentCapacity = sentCapacity;
        this.sentLog = Collections.synchronizedMap(new BoundedSentLog());
        log.info("初始化内存 Outbox，已发送日志容量上限={}", sentCapacity);
    }

    /** 覆盖已发送日志的容量上限。 */
    public void setSentCapacity(int sentCapacity) {
        if (sentCapacity <= 0) {
            throw new IllegalArgumentException("sentCapacity 必须为正数: " + sentCapacity);
        }
        this.sentCapacity = sentCapacity;
        log.info("更新内存 Outbox 已发送日志容量上限={}", sentCapacity);
    }

    /** 当前已发送日志容量上限。 */
    public int getSentCapacity() {
        return sentCapacity;
    }

    /** 已发送日志当前大小（主要用于测试与监控）。 */
    public int sentSize() {
        return sentLog.size();
    }

    @Override
    public OutboxRecord append(String topic, String payload) {
        String id = UUID.randomUUID().toString();
        OutboxRecord record =
                OutboxRecord.builder(id, topic, payload).status(OutboxStatus.PENDING).build();
        pending.put(id, record);
        log.info("Outbox 写入待投递消息 id={} topic={} 当前待投递数={}", id, topic, pending.size());
        return record;
    }

    @Override
    public synchronized void markSent(String id) {
        OutboxRecord current = pending.remove(id);
        if (current != null) {
            // 投递成功：移出待投递集合，仅保留在带上限的已发送日志中做幂等去重
            sentLog.put(id, current.sent());
            log.info("Outbox 标记投递成功 id={} 当前待投递数={}", id, pending.size());
            return;
        }
        if (sentLog.containsKey(id)) {
            // 幂等：之前已成功投递过
            log.debug("Outbox 幂等标记，id={} 此前已投递", id);
            return;
        }
        throw new OutboxException("Outbox 记录 " + id + " 不存在，无法标记投递");
    }

    @Override
    public List<OutboxRecord> pending() {
        return pending.values().stream()
                .filter(r -> r.status() == OutboxStatus.PENDING)
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 带容量上限的 LRU 已发送日志：写入超过上限时淘汰最旧（最近最少访问）记录。 */
    private final class BoundedSentLog extends LinkedHashMap<String, OutboxRecord> {

        BoundedSentLog() {
            super(Math.min(sentCapacity, 1 << 16), 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, OutboxRecord> eldest) {
            return size() > InMemoryOutboxStore.this.sentCapacity;
        }
    }
}
