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

import java.util.List;

/**
 * 本地消息表存储抽象。可插拔实现（内存、数据库表）。
 *
 * <p>约定：{@link #markSent} 在记录不存在时抛 {@link OutboxException}。
 */
public interface OutboxStore {

    /** 追加一条待投递消息，返回带生成 id 与 PENDING 状态的记录。 */
    OutboxRecord append(String topic, String payload);

    /** 标记某条记录已投递。 */
    void markSent(String id);

    /** 取出所有尚未投递的记录，按写入时间升序。 */
    List<OutboxRecord> pending();
}
