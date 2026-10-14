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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotentStoreTest {

    private final InMemoryIdempotentStore store = new InMemoryIdempotentStore();

    @Test
    void getOrCreate_首次返回进行中记录() {
        IdempotencyKey key = store.getOrCreate("order-1", "fp-a");
        assertThat(key.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(key.key()).isEqualTo("order-1");
    }

    @Test
    void getOrCreate_同指纹重复调用返回同一份() {
        IdempotencyKey first = store.getOrCreate("order-1", "fp-a");
        IdempotencyKey second = store.getOrCreate("order-1", "fp-a");
        assertThat(second).isSameAs(first);
    }

    @Test
    void getOrCreate_指纹不一致抛冲突异常() {
        store.getOrCreate("order-1", "fp-a");
        assertThatThrownBy(() -> store.getOrCreate("order-1", "fp-b"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("order-1");
    }

    @Test
    void complete_标记成功并保留结果() {
        store.getOrCreate("order-1", "fp-a");
        store.complete("order-1", "done");
        Optional<IdempotencyKey> found = store.find("order-1");
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(found.get().result()).isEqualTo("done");
        assertThat(store.isDone("order-1")).isTrue();
    }

    @Test
    void fail_标记失败并保留错误() {
        store.getOrCreate("order-1", "fp-a");
        store.fail("order-1", "boom");
        IdempotencyKey found = store.find("order-1").orElseThrow();
        assertThat(found.status()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(found.error()).isEqualTo("boom");
    }

    @Test
    void complete_键不存在抛异常() {
        assertThatThrownBy(() -> store.complete("missing", "r"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_键不存在抛异常() {
        assertThatThrownBy(() -> store.fail("missing", "e"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void find_不存在返回空() {
        assertThat(store.find("nope")).isNotPresent();
    }

    @Test
    void isDone_未登记时返回false() {
        assertThat(store.isDone("nope")).isFalse();
    }

    @Test
    void 并发同指纹只创建一份进行中记录() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<IdempotencyKey>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> store.getOrCreate("k", "fp-a")));
        }
        List<IdempotencyKey> results = new ArrayList<>();
        for (java.util.concurrent.Future<IdempotencyKey> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        assertThat(results).hasSize(threads);
        assertThat(results).allMatch(k -> "k".equals(k.key()));
        assertThat(results.stream().distinct().count()).isEqualTo(1);
        assertThat(store.find("k").orElseThrow().status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }

    @Test
    void 并发不同指纹只有一个成功其余冲突() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> store.getOrCreate("k", "fp-" + idx)));
        }
        int ok = 0;
        int conflicts = 0;
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get();
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof IdempotencyConflictException) {
                    conflicts++;
                } else {
                    throw e;
                }
            }
        }
        pool.shutdown();
        assertThat(ok).isGreaterThanOrEqualTo(1);
        assertThat(ok + conflicts).isEqualTo(threads);
    }

    @Test
    void complete后同指纹重放返回成功记录() {
        store.getOrCreate("order-1", "fp-a");
        store.complete("order-1", "cached");
        IdempotencyKey replay = store.getOrCreate("order-1", "fp-a");
        assertThat(replay.status()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(replay.result()).isEqualTo("cached");
    }

    @Test
    void complete后不同指纹仍冲突() {
        store.getOrCreate("order-1", "fp-a");
        store.complete("order-1", "cached");
        assertThatThrownBy(() -> store.getOrCreate("order-1", "fp-b"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void 构造函数拒绝非正容量() {
        assertThatThrownBy(() -> new InMemoryIdempotentStore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryIdempotentStore(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 超过容量淘汰最旧记录且保留最新记录() {
        int capacity = 100;
        InMemoryIdempotentStore bounded = new InMemoryIdempotentStore(capacity);
        for (int i = 0; i < 150; i++) {
            bounded.getOrCreate("k-" + i, "fp-" + i);
        }
        // 最旧的 k-0 已被 LRU 淘汰
        assertThat(bounded.find("k-0")).isNotPresent();
        // 次旧区间同样被淘汰
        assertThat(bounded.find("k-49")).isNotPresent();
        // 较新的记录保留
        assertThat(bounded.find("k-99")).isPresent();
        assertThat(bounded.find("k-149")).isPresent();
        // 总量不超过容量上限
        assertThat(bounded.size()).isLessThanOrEqualTo(capacity);
        assertThat(bounded.getCapacity()).isEqualTo(capacity);
    }

    @Test
    void setCapacity_可动态调整上限() {
        InMemoryIdempotentStore bounded = new InMemoryIdempotentStore(10);
        assertThat(bounded.getCapacity()).isEqualTo(10);
        bounded.setCapacity(50);
        assertThat(bounded.getCapacity()).isEqualTo(50);
        assertThatThrownBy(() -> bounded.setCapacity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 淘汰后仍可正常登记与查询() {
        InMemoryIdempotentStore bounded = new InMemoryIdempotentStore(2);
        bounded.getOrCreate("a", "fp-a");
        bounded.getOrCreate("b", "fp-b");
        bounded.getOrCreate("c", "fp-c"); // 触发淘汰 a
        assertThat(bounded.find("a")).isNotPresent();
        bounded.getOrCreate("a", "fp-a2"); // 重新登记合法
        assertThat(bounded.find("a")).isPresent();
    }
}
