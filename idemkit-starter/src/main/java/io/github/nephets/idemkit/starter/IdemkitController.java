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

import io.github.nephets.idemkit.core.idempotency.IdempotencyConflictException;
import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.outbox.OutboxException;
import io.github.nephets.idemkit.core.outbox.OutboxRecord;
import io.github.nephets.idemkit.core.outbox.OutboxStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 暴露幂等与 Outbox 能力的最小 REST 接口。 */
@RestController
@RequestMapping("/idemkit")
public class IdemkitController {

    private static final Logger log = LoggerFactory.getLogger(IdemkitController.class);

    private final IdempotentStore idempotentStore;
    private final OutboxStore outboxStore;

    public IdemkitController(IdempotentStore idempotentStore, OutboxStore outboxStore) {
        this.idempotentStore = idempotentStore;
        this.outboxStore = outboxStore;
    }

    @PostMapping("/idempotent")
    public ResponseEntity<IdempotentResponse> idempotent(@RequestBody IdempotentRequest req) {
        log.info("幂等登记请求 key={} tenant={}", req.key(), IdemkitTenantContext.get());
        try {
            io.github.nephets.idemkit.core.idempotency.IdempotencyKey key =
                    idempotentStore.getOrCreate(req.key(), req.fingerprint());
            return ResponseEntity.ok(IdempotentResponse.from(key, IdemkitTenantContext.get()));
        } catch (IdempotencyConflictException e) {
            log.debug("幂等键冲突 key={}", e.key());
            return ResponseEntity.status(409)
                    .body(IdempotentResponse.conflict(e.key(), e.getMessage()));
        }
    }

    @GetMapping("/idempotent/{key}")
    public ResponseEntity<IdempotentResponse> getKey(@PathVariable String key) {
        log.info("查询幂等键 key={}", key);
        return idempotentStore
                .find(key)
                .map(k -> ResponseEntity.ok(IdempotentResponse.from(k, IdemkitTenantContext.get())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/outbox")
    public ResponseEntity<OutboxResponse> append(@RequestBody OutboxRequest req) {
        log.info("Outbox 追加消息请求 topic={} tenant={}", req.topic(), IdemkitTenantContext.get());
        OutboxRecord record = outboxStore.append(req.topic(), req.payload());
        return ResponseEntity.ok(OutboxResponse.from(record));
    }

    @GetMapping("/outbox/pending")
    public List<OutboxResponse> pending() {
        log.info("查询 Outbox 待投递列表");
        return outboxStore.pending().stream().map(OutboxResponse::from).toList();
    }

    @PostMapping("/outbox/{id}/sent")
    public ResponseEntity<Void> markSent(@PathVariable String id) {
        log.info("Outbox 标记投递请求 id={}", id);
        try {
            outboxStore.markSent(id);
            return ResponseEntity.ok().build();
        } catch (OutboxException e) {
            log.debug("Outbox 标记投递未找到 id={}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
