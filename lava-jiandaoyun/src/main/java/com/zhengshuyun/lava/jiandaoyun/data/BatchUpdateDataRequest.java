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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 修改多条数据的不可变请求参数：把同一组字段值写入多条数据，单次最多 100 条。
 *
 * <p>不支持子表单字段；附件和图片字段会覆盖原有文件。</p>
 */
public final class BatchUpdateDataRequest {
    /** 应用 ID。 */
    private final String appId;
    /** 表单 ID。 */
    private final String entryId;
    /** 待修改的数据 ID 列表。 */
    private final List<String> dataIds;
    /** 写入每条数据的字段值。 */
    private final Map<String, @Nullable Object> data;
    /** 事务 ID，数据含附件或图片时必填。 */
    private final @Nullable String transactionId;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private BatchUpdateDataRequest(Builder builder) {
        appId = JiandaoyunValidationUtils.requireAppId(builder.appId);
        entryId = JiandaoyunValidationUtils.requireEntryId(builder.entryId);
        dataIds = ValidationUtils.requireNonNull(builder.dataIds, "dataIds is required");
        data = ValidationUtils.requireNonNull(builder.data, "data is required");
        transactionId = builder.transactionId;
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
     * 返回待修改的数据 ID 列表。
     *
     * @return 不可变数据 ID 列表
     */
    public List<String> dataIds() {
        return dataIds;
    }

    /**
     * 返回未包装的字段值。
     *
     * @return 字段名到值的不可变映射
     */
    public Map<String, @Nullable Object> data() {
        return data;
    }

    /**
     * 返回事务 ID。
     *
     * @return 事务 ID；未配置时为 {@code null}
     */
    public @Nullable String transactionId() {
        return transactionId;
    }

    /**
     * 修改多条数据请求构建器。
     */
    public static final class Builder {
        /** 应用 ID。 */
        private @Nullable String appId;
        /** 表单 ID。 */
        private @Nullable String entryId;
        /** 数据 ID 列表。 */
        private @Nullable List<String> dataIds;
        /** 字段值。 */
        private @Nullable Map<String, @Nullable Object> data;
        /** 事务 ID。 */
        private @Nullable String transactionId;

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
         * 配置待修改的数据 ID 列表。
         *
         * @param value 数据 ID 列表
         * @return 当前构建器
         */
        public Builder dataIds(List<String> value) {
            dataIds = List.copyOf(ValidationUtils.requireNonNull(value, "dataIds must not be null"));
            return this;
        }

        /**
         * 配置写入每条数据的字段值。
         *
         * @param value 字段名到值的映射，值无需包装
         * @return 当前构建器
         */
        public Builder data(Map<String, ?> value) {
            ValidationUtils.requireNonNull(value, "data must not be null");
            data = Collections.unmodifiableMap(new LinkedHashMap<>(value));
            return this;
        }

        /**
         * 配置事务 ID。
         *
         * @param value 与获取上传凭证时相同的事务 ID
         * @return 当前构建器
         */
        public Builder transactionId(String value) {
            transactionId = JiandaoyunValidationUtils.requireNotBlank(value, "transactionId");
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置应用 ID、表单 ID、数据 ID 列表或字段值时抛出
         */
        public BatchUpdateDataRequest build() {
            return new BatchUpdateDataRequest(this);
        }
    }
}
