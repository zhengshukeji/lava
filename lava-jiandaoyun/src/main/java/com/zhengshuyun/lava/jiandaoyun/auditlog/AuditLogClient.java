/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhengshuyun.lava.jiandaoyun.auditlog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 简道云审计日志客户端（旗舰版、专属版功能）。
 */
public final class AuditLogClient {
    /** 审计日志类型定义接口路径。 */
    private static final String DOMAINS_PATH = "/api/v1/audit_log/domains";
    /** 审计日志明细接口路径。 */
    private static final String LIST_PATH = "/api/v1/audit_log/list";
    /** 上游要求的 UTC 毫秒时间格式，如 2026-04-01T00:00:00.000Z。 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建审计日志入口。
     *
     * @param transport 共享协议传输层
     */
    public AuditLogClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询审计日志范围及各范围支持的事件类型。
     *
     * @return 日志范围列表
     */
    public List<AuditLogDomain> listDomains() {
        return transport.post(transport.endpoint(DOMAINS_PATH), Map.of(), DomainsPayload.class).domains();
    }

    /**
     * 查询审计日志明细。翻页时把返回的 {@code cursor} 传入下一次请求。
     *
     * @param request 查询参数
     * @return 日志分页结果
     */
    public AuditLogPage list(ListAuditLogsRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        boolean hasFilters = request.actorIds() != null || request.appIds() != null || request.entryIds() != null;
        return transport.post(transport.endpoint(LIST_PATH), new ListPayload(
                request.domain(),
                new TimeRangePayload(format(request.start()), format(request.end())),
                request.eventTypes(),
                request.limit(),
                request.cursor(),
                hasFilters ? new FiltersPayload(request.actorIds(), request.appIds(), request.entryIds()) : null
        ), AuditLogPage.class);
    }

    /**
     * 格式化为上游要求的 UTC 毫秒时间。
     *
     * @param value 时间
     * @return 时间文本
     */
    private static String format(Instant value) {
        return TIME_FORMAT.format(value);
    }

    /**
     * 审计日志明细请求正文。
     *
     * @param domain 日志范围
     * @param timeRange 时间范围
     * @param eventTypes 事件类型
     * @param limit 每页条数
     * @param cursor 翻页游标
     * @param filters 过滤条件
     */
    private record ListPayload(
            String domain,
            @JsonProperty("time_range") TimeRangePayload timeRange,
            @JsonProperty("event_types") @Nullable List<String> eventTypes,
            @Nullable Integer limit,
            @Nullable String cursor,
            @Nullable FiltersPayload filters
    ) {
    }

    /**
     * 时间范围请求结构。
     *
     * @param start 开始时间
     * @param end 结束时间
     */
    private record TimeRangePayload(String start, String end) {
    }

    /**
     * 过滤条件请求结构。
     *
     * @param actorIds 操作者 ID
     * @param appIds 应用 ID
     * @param entryIds 表单 ID
     */
    private record FiltersPayload(
            @JsonProperty("actor_ids") @Nullable List<String> actorIds,
            @JsonProperty("app_ids") @Nullable List<String> appIds,
            @JsonProperty("entry_ids") @Nullable List<String> entryIds
    ) {
    }

    /**
     * 日志范围响应正文。
     *
     * @param domains 日志范围列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DomainsPayload(List<AuditLogDomain> domains) {
    }
}
