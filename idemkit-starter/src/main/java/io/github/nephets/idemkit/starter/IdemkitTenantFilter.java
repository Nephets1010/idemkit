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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/** 从配置的头里取出租户号放进 {@link IdemkitTenantContext}，请求结束清空。 */
@Order(0)
public class IdemkitTenantFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IdemkitTenantFilter.class);

    private final String tenantHeader;

    public IdemkitTenantFilter(IdemkitProperties properties) {
        this.tenantHeader = properties.getTenantHeader();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String tenant = http.getHeader(tenantHeader);
        if (tenant != null && !tenant.isEmpty()) {
            IdemkitTenantContext.set(tenant);
            log.debug("解析到租户 tenant={} header={}", tenant, tenantHeader);
        } else {
            log.debug("未携带租户头 header={}，按默认租户处理", tenantHeader);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            IdemkitTenantContext.clear();
        }
    }
}
