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

package com.zhengshuyun.lava.jiandaoyun.data;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 查询多条数据的不可变请求参数。
 *
 * <p>结果按数据 ID 升序返回；翻页时把上一页最后一条数据的 ID 作为 {@code dataId} 传入。
 * 需要取全部数据时直接使用 {@link DataClient#listAll(ListDataRequest)}。</p>
 */
public final class ListDataRequest {
    /** 应用 ID。 */
    private final String appId;
    /** 表单 ID。 */
    private final String entryId;
    /** 翻页游标：上一页最后一条数据的 ID。 */
    private final @Nullable String dataId;
    /** 需要返回的字段；未配置时返回全部字段。 */
    private final @Nullable List<String> fields;
    /** 数据过滤条件。 */
    private final @Nullable DataFilter filter;
    /** 单次取数条数，1~100，简道云默认 10。 */
    private final @Nullable Integer limit;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private ListDataRequest(Builder builder) {
        appId = JiandaoyunValidationUtils.requireAppId(builder.appId);
        entryId = JiandaoyunValidationUtils.requireEntryId(builder.entryId);
        dataId = builder.dataId;
        fields = builder.fields;
        filter = builder.filter;
        limit = builder.limit;
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
     * 返回应用 ID。
     *
     * @return 应用 ID
     */
    public String appId() {
        return appId;
    }

    /**
     * 返回表单 ID。
     *
     * @return 表单 ID
     */
    public String entryId() {
        return entryId;
    }

    /**
     * 返回翻页游标。
     *
     * @return 上一页最后一条数据的 ID；未配置时为 {@code null}
     */
    public @Nullable String dataId() {
        return dataId;
    }

    /**
     * 返回需要查询的字段。
     *
     * @return 字段名列表；未配置时为 {@code null}
     */
    public @Nullable List<String> fields() {
        return fields;
    }

    /**
     * 返回过滤条件。
     *
     * @return 过滤条件；未配置时为 {@code null}
     */
    public @Nullable DataFilter filter() {
        return filter;
    }

    /**
     * 返回单次取数条数。
     *
     * @return 取数条数；未配置时为 {@code null}，由简道云按 10 处理
     */
    public @Nullable Integer limit() {
        return limit;
    }

    /**
     * 多条数据查询请求构建器。
     */
    public static final class Builder {
        /** 应用 ID。 */
        private @Nullable String appId;
        /** 表单 ID。 */
        private @Nullable String entryId;
        /** 翻页游标。 */
        private @Nullable String dataId;
        /** 查询字段。 */
        private @Nullable List<String> fields;
        /** 过滤条件。 */
        private @Nullable DataFilter filter;
        /** 取数条数。 */
        private @Nullable Integer limit;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置应用 ID。
         *
         * @param value 应用 ID
         * @return 当前构建器
         */
        public Builder appId(String value) {
            appId = value;
            return this;
        }

        /**
         * 配置表单 ID。
         *
         * @param value 表单 ID
         * @return 当前构建器
         */
        public Builder entryId(String value) {
            entryId = value;
            return this;
        }

        /**
         * 配置翻页游标。
         *
         * @param value 上一页最后一条数据的 ID
         * @return 当前构建器
         */
        public Builder dataId(String value) {
            dataId = JiandaoyunValidationUtils.requireNotBlank(value, "dataId");
            return this;
        }

        /**
         * 配置只返回指定字段（不支持子表单内字段）。
         *
         * @param value 字段名
         * @return 当前构建器
         */
        public Builder fields(String... value) {
            return fields(List.of(value));
        }

        /**
         * 配置只返回指定字段（不支持子表单内字段）。
         *
         * @param value 字段名列表
         * @return 当前构建器
         */
        public Builder fields(List<String> value) {
            fields = List.copyOf(ValidationUtils.requireNonNull(value, "fields must not be null"));
            return this;
        }

        /**
         * 配置过滤条件。
         *
         * @param value 过滤条件
         * @return 当前构建器
         */
        public Builder filter(DataFilter value) {
            filter = ValidationUtils.requireNonNull(value, "filter must not be null");
            return this;
        }

        /**
         * 配置单次取数条数。
         *
         * @param value 取数条数，范围 1~100
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置应用 ID、表单 ID 或 limit 非正数时抛出
         */
        public ListDataRequest build() {
            return new ListDataRequest(this);
        }
    }
}
