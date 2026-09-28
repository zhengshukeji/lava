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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * 审计日志记录。
 *
 * @param eventId 事件 ID
 * @param eventTime 事件时间
 * @param eventType 标准事件类型，如 data.record.create
 * @param domain 日志范围
 * @param actor 操作者
 * @param resource 被操作的资源
 * @param event 事件分类与结果
 * @param detail 事件明细，结构随事件类型变化
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLog(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_time") @Nullable Instant eventTime,
        @JsonProperty("event_type") @Nullable String eventType,
        @Nullable String domain,
        @Nullable Actor actor,
        @Nullable Resource resource,
        @Nullable Event event,
        @Nullable Map<String, Object> detail
) {
    /**
     * 操作者。
     *
     * @param type 操作者类型，如 user
     * @param id 操作者 ID
     * @param name 操作者名称
     * @param ip 来源 IP
     * @param userAgent 客户端标识
     * @param geo 地理位置，结构由简道云定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Actor(
            @Nullable String type,
            @Nullable String id,
            @Nullable String name,
            @Nullable String ip,
            @JsonProperty("user_agent") @Nullable String userAgent,
            @Nullable Map<String, Object> geo
    ) {
    }

    /**
     * 被操作的资源。
     *
     * @param type 资源类型，如 form
     * @param id 资源 ID
     * @param name 资源名称
     * @param parentId 上级资源 ID
     * @param parentType 上级资源类型，如 app
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resource(
            @Nullable String type,
            @Nullable String id,
            @Nullable String name,
            @JsonProperty("parent_id") @Nullable String parentId,
            @JsonProperty("parent_type") @Nullable String parentType
    ) {
    }

    /**
     * 事件分类与结果。
     *
     * @param category 事件类别，如 data
     * @param action 动作，如 print
     * @param outcome 结果，如 success
     * @param severity 严重级别，如 info
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            @Nullable String category,
            @Nullable String action,
            @Nullable String outcome,
            @Nullable String severity
    ) {
    }
}
