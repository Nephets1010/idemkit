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

/** SAGA 中的一个步骤。正向 {@link #execute} 成功后才算完成；失败时编排器会按相反顺序调用已完成步骤的 {@link #compensate} 做回滚。 */
public interface SagaStep {

    /** 步骤名称，用于日志与上下文记录。 */
    String name();

    /** 正向执行。抛异常表示本步失败，触发重试（按策略）与补偿。 */
    void execute(SagaContext ctx) throws Exception;

    /** 补偿（回滚）操作。 */
    void compensate(SagaContext ctx) throws Exception;
}
