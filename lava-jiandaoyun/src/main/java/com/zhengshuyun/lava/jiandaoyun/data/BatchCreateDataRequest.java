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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新建多条数据的不可变请求参数，单次最多 100 条。
 *
 * <p>批量新建不校验字段必填和重复值，不支持分割线、手写签名、选择数据和流水号字段。</p>
 */
public final class BatchCreateDataRequest {
    /** 应用 ID。 */
    private final String appId;
    /** 表单 ID。 */
    private final String entryId;
    /** 多条数据内容。 */
    private final List<Map<String, @Nullable Object>> dataList;
    /** 数据提交人的成员编号。 */
    private final @Nullable String dataCreator;
    /** 是否发起流程（仅流程表单有效）。 */
    private final @Nullable Boolean startWorkflow;
    /** 事务 ID，1 小时内相同事务 ID 的请求会被去重。 */
    private final @Nullable String transactionId;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private BatchCreateDataRequest(Builder builder) {
        appId = JiandaoyunValidationUtils.requireAppId(builder.appId);
        entryId = JiandaoyunValidationUtils.requireEntryId(builder.entryId);
        dataList = ValidationUtils.requireNonNull(builder.dataList, "dataList is required");
        dataCreator = builder.dataCreator;
        startWorkflow = builder.startWorkflow;
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
     * 返回未包装的多条数据。
     *
     * @return 不可变数据列表
     */
    public List<Map<String, @Nullable Object>> dataList() {
        return dataList;
    }

    /**
     * 返回数据提交人。
     *
     * @return 成员编号；未配置时为 {@code null}
     */
    public @Nullable String dataCreator() {
        return dataCreator;
    }

    /**
     * 返回是否发起流程。
     *
     * @return 未配置时为 {@code null}
     */
    public @Nullable Boolean startWorkflow() {
        return startWorkflow;
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
     * 新建多条数据请求构建器。
     */
    public static final class Builder {
        /** 应用 ID。 */
        private @Nullable String appId;
        /** 表单 ID。 */
        private @Nullable String entryId;
        /** 多条数据内容。 */
        private @Nullable List<Map<String, @Nullable Object>> dataList;
        /** 数据提交人。 */
        private @Nullable String dataCreator;
        /** 是否发起流程。 */
        private @Nullable Boolean startWorkflow;
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
         * 配置多条数据内容。
         *
         * @param value 每条数据为字段名到值的映射，值无需包装
         * @return 当前构建器
         */
        public Builder dataList(List<? extends Map<String, ?>> value) {
            ValidationUtils.requireNonNull(value, "dataList must not be null");
            List<Map<String, @Nullable Object>> copy = new ArrayList<>(value.size());
            value.forEach(data -> copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(
                    ValidationUtils.requireNonNull(data, "dataList must not contain null")))));
            dataList = Collections.unmodifiableList(copy);
            return this;
        }

        /**
         * 配置数据提交人。
         *
         * @param value 成员编号
         * @return 当前构建器
         */
        public Builder dataCreator(String value) {
            dataCreator = JiandaoyunValidationUtils.requireNotBlank(value, "dataCreator");
            return this;
        }

        /**
         * 配置是否发起流程。
         *
         * @param value 为 {@code true} 时发起流程
         * @return 当前构建器
         */
        public Builder startWorkflow(boolean value) {
            startWorkflow = value;
            return this;
        }

        /**
         * 配置事务 ID。
         *
         * @param value 事务 ID，建议使用 UUID
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
         * @throws IllegalArgumentException 未配置应用 ID、表单 ID 或数据列表时抛出
         */
        public BatchCreateDataRequest build() {
            return new BatchCreateDataRequest(this);
        }
    }
}
