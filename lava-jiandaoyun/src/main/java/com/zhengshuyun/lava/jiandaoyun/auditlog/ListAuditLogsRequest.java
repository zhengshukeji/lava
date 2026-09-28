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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * 查询审计日志明细的不可变请求参数。时间跨度最长 31 天。
 */
public final class ListAuditLogsRequest {
    /** 日志范围。 */
    private final String domain;
    /** 查询开始时间。 */
    private final Instant start;
    /** 查询结束时间。 */
    private final Instant end;
    /** 事件类型；未配置时查询该范围的全部类型。 */
    private final @Nullable List<String> eventTypes;
    /** 每页条数，默认 200，最大 200。 */
    private final @Nullable Integer limit;
    /** 翻页游标。 */
    private final @Nullable String cursor;
    /** 操作者 ID 过滤。 */
    private final @Nullable List<String> actorIds;
    /** 应用 ID 过滤。 */
    private final @Nullable List<String> appIds;
    /** 表单 ID 过滤。 */
    private final @Nullable List<String> entryIds;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private ListAuditLogsRequest(Builder builder) {
        domain = JiandaoyunValidationUtils.requireNotBlank(builder.domain, "domain");
        start = ValidationUtils.requireNonNull(builder.start, "start is required");
        end = ValidationUtils.requireNonNull(builder.end, "end is required");
        ValidationUtils.requireTrue(!end.isBefore(start), "end must not be before start");
        eventTypes = builder.eventTypes;
        limit = builder.limit;
        cursor = builder.cursor;
        actorIds = builder.actorIds;
        appIds = builder.appIds;
        entryIds = builder.entryIds;
        if (limit != null) {
            ValidationUtils.requireTrue(limit >= 1, "limit must be positive");
        }
    }

    /**
     * 创建请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回日志范围。
     *
     * @return 日志范围
     */
    public String domain() {
        return domain;
    }

    /**
     * 返回查询开始时间。
     *
     * @return 开始时间
     */
    public Instant start() {
        return start;
    }

    /**
     * 返回查询结束时间。
     *
     * @return 结束时间
     */
    public Instant end() {
        return end;
    }

    /**
     * 返回事件类型过滤。
     *
     * @return 事件类型；未配置时为 {@code null}
     */
    public @Nullable List<String> eventTypes() {
        return eventTypes;
    }

    /**
     * 返回每页条数。
     *
     * @return 每页条数；未配置时为 {@code null}
     */
    public @Nullable Integer limit() {
        return limit;
    }

    /**
     * 返回翻页游标。
     *
     * @return 游标；首页为 {@code null}
     */
    public @Nullable String cursor() {
        return cursor;
    }

    /**
     * 返回操作者 ID 过滤。
     *
     * @return 操作者 ID；未配置时为 {@code null}
     */
    public @Nullable List<String> actorIds() {
        return actorIds;
    }

    /**
     * 返回应用 ID 过滤。
     *
     * @return 应用 ID；未配置时为 {@code null}
     */
    public @Nullable List<String> appIds() {
        return appIds;
    }

    /**
     * 返回表单 ID 过滤。
     *
     * @return 表单 ID；未配置时为 {@code null}
     */
    public @Nullable List<String> entryIds() {
        return entryIds;
    }

    /**
     * 审计日志请求构建器。
     */
    public static final class Builder {
        /** 日志范围。 */
        private @Nullable String domain;
        /** 开始时间。 */
        private @Nullable Instant start;
        /** 结束时间。 */
        private @Nullable Instant end;
        /** 事件类型。 */
        private @Nullable List<String> eventTypes;
        /** 每页条数。 */
        private @Nullable Integer limit;
        /** 翻页游标。 */
        private @Nullable String cursor;
        /** 操作者 ID。 */
        private @Nullable List<String> actorIds;
        /** 应用 ID。 */
        private @Nullable List<String> appIds;
        /** 表单 ID。 */
        private @Nullable List<String> entryIds;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置日志范围。
         *
         * @param value 日志范围，如 login、platform、app_builder、kms
         * @return 当前构建器
         */
        public Builder domain(String value) {
            domain = value;
            return this;
        }

        /**
         * 配置查询时间范围。
         *
         * @param startValue 开始时间
         * @param endValue 结束时间，与开始时间相差不超过 31 天
         * @return 当前构建器
         */
        public Builder timeRange(Instant startValue, Instant endValue) {
            start = ValidationUtils.requireNonNull(startValue, "start must not be null");
            end = ValidationUtils.requireNonNull(endValue, "end must not be null");
            return this;
        }

        /**
         * 配置事件类型过滤。
         *
         * @param value 事件类型
         * @return 当前构建器
         */
        public Builder eventTypes(String... value) {
            return eventTypes(List.of(value));
        }

        /**
         * 配置事件类型过滤。
         *
         * @param value 事件类型列表
         * @return 当前构建器
         */
        public Builder eventTypes(List<String> value) {
            eventTypes = copy(value, "eventTypes");
            return this;
        }

        /**
         * 配置每页条数。
         *
         * @param value 每页条数，范围 1~200
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置翻页游标。
         *
         * @param value 上一页返回的游标
         * @return 当前构建器
         */
        public Builder cursor(String value) {
            cursor = JiandaoyunValidationUtils.requireNotBlank(value, "cursor");
            return this;
        }

        /**
         * 按操作者过滤。
         *
         * @param value 操作者 ID 列表
         * @return 当前构建器
         */
        public Builder actorIds(List<String> value) {
            actorIds = copy(value, "actorIds");
            return this;
        }

        /**
         * 按应用过滤。
         *
         * @param value 应用 ID 列表
         * @return 当前构建器
         */
        public Builder appIds(List<String> value) {
            appIds = copy(value, "appIds");
            return this;
        }

        /**
         * 按表单过滤。
         *
         * @param value 表单 ID 列表
         * @return 当前构建器
         */
        public Builder entryIds(List<String> value) {
            entryIds = copy(value, "entryIds");
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置日志范围或时间范围、结束早于开始时抛出
         */
        public ListAuditLogsRequest build() {
            return new ListAuditLogsRequest(this);
        }

        /**
         * 复制列表参数。
         *
         * @param value 列表
         * @param name 参数名
         * @return 不可变副本
         */
        private static List<String> copy(List<String> value, String name) {
            return List.copyOf(ValidationUtils.requireNonNull(value, name + " must not be null"));
        }
    }
}
