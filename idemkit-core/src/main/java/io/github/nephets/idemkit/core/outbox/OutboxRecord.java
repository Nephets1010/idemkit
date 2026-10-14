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

import java.time.Instant;
import java.util.Objects;

/**
 * 一条待投递的消息。业务写库与写 Outbox 在同一事务里完成，后台定时任务扫描 {@link OutboxStatus#PENDING} 记录并投递。
 *
 * <p>本类不可变，投递成功后由 {@link #sent()} 返回带新状态的新副本。
 */
public final class OutboxRecord {

    private final String id;
    private final String topic;
    private final String payload;
    private final OutboxStatus status;
    private final Instant createdAt;
    private final Instant sentAt;

    private OutboxRecord(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id 不能为空");
        this.topic = Objects.requireNonNull(b.topic, "topic 不能为空");
        this.payload = Objects.requireNonNull(b.payload, "payload 不能为空");
        this.status = Objects.requireNonNull(b.status, "status 不能为空");
        this.createdAt = b.createdAt;
        this.sentAt = b.sentAt;
    }

    public static Builder builder(String id, String topic, String payload) {
        return new Builder(id, topic, payload);
    }

    /** 标记为已投递，返回新副本。 */
    public OutboxRecord sent() {
        return toBuilder().status(OutboxStatus.SENT).sentAt(Instant.now()).build();
    }

    public String id() {
        return id;
    }

    public String topic() {
        return topic;
    }

    public String payload() {
        return payload;
    }

    public OutboxStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    private Builder toBuilder() {
        return builder(id, topic, payload).status(status).createdAt(createdAt).sentAt(sentAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboxRecord)) {
            return false;
        }
        OutboxRecord that = (OutboxRecord) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OutboxRecord{id='" + id + "', topic='" + topic + "', status=" + status + '}';
    }

    /** 构造器。 */
    public static final class Builder {
        private final String id;
        private final String topic;
        private final String payload;
        private OutboxStatus status = OutboxStatus.PENDING;
        private Instant createdAt = Instant.now();
        private Instant sentAt;

        private Builder(String id, String topic, String payload) {
            this.id = id;
            this.topic = topic;
            this.payload = payload;
        }

        public Builder status(OutboxStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder sentAt(Instant sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public OutboxRecord build() {
            return new OutboxRecord(this);
        }
    }
}
