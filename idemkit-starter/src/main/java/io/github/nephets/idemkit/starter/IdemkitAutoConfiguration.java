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
package io.github.nephets.idemkit.starter;

import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.idempotency.InMemoryIdempotentStore;
import io.github.nephets.idemkit.core.outbox.InMemoryOutboxStore;
import io.github.nephets.idemkit.core.outbox.OutboxStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** idemkit 的 Spring Boot 自动装配。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "idemkit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(IdemkitProperties.class)
public class IdemkitAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdemkitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public IdempotentStore idempotentStore() {
        log.info("装配内存幂等存储 IdempotentStore");
        return new InMemoryIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxStore outboxStore() {
        log.info("装配内存 Outbox 存储 OutboxStore");
        return new InMemoryOutboxStore();
    }

    @Bean
    public IdemkitTenantFilter idemkitTenantFilter(IdemkitProperties properties) {
        log.info("装配租户解析过滤器，租户头={}", properties.getTenantHeader());
        return new IdemkitTenantFilter(properties);
    }

    @Bean
    public IdemkitController idemkitController(
            IdempotentStore idempotentStore, OutboxStore outboxStore) {
        log.info("装配幂等/Outbox REST 控制器");
        return new IdemkitController(idempotentStore, outboxStore);
    }
}
