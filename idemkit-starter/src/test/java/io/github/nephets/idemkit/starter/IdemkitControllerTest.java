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
import io.github.nephets.idemkit.core.idempotency.IdempotencyKey;
import io.github.nephets.idemkit.core.idempotency.IdempotentStore;
import io.github.nephets.idemkit.core.outbox.OutboxException;
import io.github.nephets.idemkit.core.outbox.OutboxRecord;
import io.github.nephets.idemkit.core.outbox.OutboxStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdemkitController.class)
class IdemkitControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private IdempotentStore idempotentStore;
    @MockBean private OutboxStore outboxStore;

    @Test
    void 登记幂等键_返回进行中() throws Exception {
        given(idempotentStore.getOrCreate("k1", "fp-a"))
                .willReturn(IdempotencyKey.pending("k1", "fp-a"));
        mockMvc.perform(
                        post("/idemkit/idempotent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"k1\",\"fingerprint\":\"fp-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.key").value("k1"));
    }

    @Test
    void 指纹冲突_返回409() throws Exception {
        given(idempotentStore.getOrCreate(eq("k1"), eq("fp-b")))
                .willThrow(new IdempotencyConflictException("k1", "指纹不一致"));
        mockMvc.perform(
                        post("/idemkit/idempotent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"k1\",\"fingerprint\":\"fp-b\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CONFLICT"));
    }

    @Test
    void 查询已存在幂等键_返回200() throws Exception {
        given(idempotentStore.find("k1"))
                .willReturn(Optional.of(IdempotencyKey.pending("k1", "fp-a")));
        mockMvc.perform(get("/idemkit/idempotent/k1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("k1"));
    }

    @Test
    void 查询缺失幂等键_返回404() throws Exception {
        given(idempotentStore.find("missing")).willReturn(Optional.empty());
        mockMvc.perform(get("/idemkit/idempotent/missing")).andExpect(status().isNotFound());
    }

    @Test
    void 写入Outbox_返回记录() throws Exception {
        given(outboxStore.append("order.created", "{}"))
                .willReturn(OutboxRecord.builder("id-1", "order.created", "{}").build());
        mockMvc.perform(
                        post("/idemkit/outbox")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topic\":\"order.created\",\"payload\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 待投递列表_返回pending() throws Exception {
        given(outboxStore.pending())
                .willReturn(List.of(OutboxRecord.builder("id-1", "t", "p").build()));
        mockMvc.perform(get("/idemkit/outbox/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("id-1"));
    }

    @Test
    void 标记投递_记录不存在返回404() throws Exception {
        org.mockito.BDDMockito.willThrow(new OutboxException("not found"))
                .given(outboxStore)
                .markSent("x");
        mockMvc.perform(post("/idemkit/outbox/x/sent")).andExpect(status().isNotFound());
    }

    @Test
    void 标记投递_成功返回200() throws Exception {
        mockMvc.perform(post("/idemkit/outbox/id-1/sent")).andExpect(status().isOk());
    }
}
