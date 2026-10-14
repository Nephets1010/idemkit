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

import java.time.Instant;
import java.util.Objects;

/**
 * 一条幂等记录。同一个 {@code key} 在存储里只存在一份，靠 {@code fingerprint} 区分请求内容是否一致。
 *
 * <p>本类是不可变的：状态流转（成功 / 失败）会返回新的副本，原对象不变。
 */
public final class IdempotencyKey {

    private final String key;
    private final String fingerprint;
    private final IdempotencyStatus status;
    private final String result;
    private final String error;
    private final Instant createdAt;
    private final Instant completedAt;

    private IdempotencyKey(Builder b) {
        this.key = Objects.requireNonNull(b.key, "key 不能为空");
        this.fingerprint = Objects.requireNonNull(b.fingerprint, "fingerprint 不能为空");
        this.status = Objects.requireNonNull(b.status, "status 不能为空");
        this.result = b.result;
        this.error = b.error;
        this.createdAt = b.createdAt;
        this.completedAt = b.completedAt;
    }

    /** 生成一条处于进行中状态的记录。 */
    public static IdempotencyKey pending(String key, String fingerprint) {
        return builder(key, fingerprint).status(IdempotencyStatus.IN_PROGRESS).build();
    }

    public static Builder builder(String key, String fingerprint) {
        return new Builder(key, fingerprint);
    }

    /** 标记成功，返回新副本并带上执行结果。 */
    public IdempotencyKey completed(String result) {
        return toBuilder()
                .status(IdempotencyStatus.SUCCESS)
                .result(result)
                .completedAt(Instant.now())
                .build();
    }

    /** 标记失败，返回新副本并带上错误信息。 */
    public IdempotencyKey failed(String error) {
        return toBuilder()
                .status(IdempotencyStatus.FAILED)
                .error(error)
                .completedAt(Instant.now())
                .build();
    }

    public String key() {
        return key;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public IdempotencyStatus status() {
        return status;
    }

    public String result() {
        return result;
    }

    public String error() {
        return error;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public boolean isDone() {
        return status != IdempotencyStatus.IN_PROGRESS;
    }

    private Builder toBuilder() {
        return builder(key, fingerprint)
                .status(status)
                .result(result)
                .error(error)
                .createdAt(createdAt)
                .completedAt(completedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyKey)) {
            return false;
        }
        IdempotencyKey that = (IdempotencyKey) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "IdempotencyKey{key='" + key + "', status=" + status + ", done=" + isDone() + '}';
    }

    /** 构造器。 */
    public static final class Builder {
        private final String key;
        private final String fingerprint;
        private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;
        private String result;
        private String error;
        private Instant createdAt = Instant.now();
        private Instant completedAt;

        private Builder(String key, String fingerprint) {
            this.key = key;
            this.fingerprint = fingerprint;
        }

        public Builder status(IdempotencyStatus status) {
            this.status = status;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public IdempotencyKey build() {
            return new IdempotencyKey(this);
        }
    }
}
