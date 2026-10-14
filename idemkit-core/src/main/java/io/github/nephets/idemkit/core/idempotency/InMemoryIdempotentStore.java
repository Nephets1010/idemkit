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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 {@link LinkedHashMap} 的内存实现，单进程内可用，也方便做单元测试。生产环境应换成持久化实现。
 *
 * <p>该实现带容量上限，超过上限时按 LRU（最近最少使用）淘汰最旧记录，避免长期运行下内存无限增长导致 OOM。 默认上限为 {@link #DEFAULT_CAPACITY}（100
 * 万条），可通过构造函数或 {@link #setCapacity(int)} 覆盖。
 */
public final class InMemoryIdempotentStore implements IdempotentStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotentStore.class);

    /** 默认容量上限：100 万条幂等记录，普通内存规模下足够且不会无限增长。 */
    public static final int DEFAULT_CAPACITY = 1_000_000;

    private volatile int capacity;
    private final Map<String, IdempotencyKey> store;

    public InMemoryIdempotentStore() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 指定容量上限构造。超过上限时最旧（最近最少访问）的记录会被淘汰。
     *
     * @param capacity 容量上限，必须为正数
     */
    public InMemoryIdempotentStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必须为正数: " + capacity);
        }
        this.capacity = capacity;
        this.store = Collections.synchronizedMap(new BoundedLinkedHashMap());
        log.info("初始化内存幂等存储，容量上限={}", capacity);
    }

    /** 覆盖容量上限。已有记录不会被立即淘汰，下一次写入超出上限时触发淘汰。 */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必须为正数: " + capacity);
        }
        this.capacity = capacity;
        log.info("更新内存幂等存储容量上限={}", capacity);
    }

    /** 当前容量上限。 */
    public int getCapacity() {
        return capacity;
    }

    /** 当前记录数（近似值，主要用于测试与监控）。 */
    public int size() {
        return store.size();
    }

    @Override
    public IdempotencyKey getOrCreate(String key, String fingerprint) {
        IdempotencyKey fresh = IdempotencyKey.pending(key, fingerprint);
        IdempotencyKey existing =
                store.compute(
                        key,
                        (k, current) -> {
                            if (current == null) {
                                return fresh;
                            }
                            if (!current.fingerprint().equals(fingerprint)) {
                                throw new IdempotencyConflictException(
                                        key, "幂等键 " + key + " 已存在，但本次请求指纹与已登记记录不一致");
                            }
                            return current;
                        });
        if (existing == fresh) {
            log.info("幂等键新建 key={} 状态={}", key, existing.status());
        } else {
            log.debug("幂等键命中 key={} 状态={}", key, existing.status());
        }
        return existing;
    }

    @Override
    public Optional<IdempotencyKey> find(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void complete(String key, String result) {
        IdempotencyKey next =
                store.compute(
                        key,
                        (k, current) -> {
                            if (current == null) {
                                throw new IllegalStateException("幂等键 " + key + " 不存在，无法标记完成");
                            }
                            return current.completed(result);
                        });
        // next 仅用于消除未使用告警；compute 已写入
        if (next == null) {
            throw new IllegalStateException("幂等键 " + key + " 不存在，无法标记完成");
        }
        log.info("幂等键状态迁移 key={} 状态={}", key, next.status());
    }

    @Override
    public void fail(String key, String error) {
        store.compute(
                key,
                (k, current) -> {
                    if (current == null) {
                        throw new IllegalStateException("幂等键 " + key + " 不存在，无法标记失败");
                    }
                    return current.failed(error);
                });
        log.info("幂等键标记为失败 key={}", key);
    }

    @Override
    public boolean isDone(String key) {
        IdempotencyKey current = store.get(key);
        return current != null && current.isDone();
    }

    /** 带容量上限的 LRU {@link LinkedHashMap}：写入超过上限时淘汰最旧（最近最少访问）记录。 */
    private final class BoundedLinkedHashMap extends LinkedHashMap<String, IdempotencyKey> {

        BoundedLinkedHashMap() {
            super(Math.min(capacity, 1 << 16), 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, IdempotencyKey> eldest) {
            return size() > InMemoryIdempotentStore.this.capacity;
        }
    }
}
