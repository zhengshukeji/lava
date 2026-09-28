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
import java.util.Map;

/**
 * 新建单条数据的不可变请求参数。
 *
 * <p>{@code data} 直接写 {@code 字段名 -> 值}，SDK 会自动包装为简道云要求的 {@code {"value": 值}}；
 * 子表单写成行列表，每行同样是 {@code 字段名 -> 值}。</p>
 */
public final class CreateDataRequest {
    /** 应用 ID。 */
    private final String appId;
    /** 表单 ID。 */
    private final String entryId;
    /** 数据内容。 */
    private final Map<String, @Nullable Object> data;
    /** 数据提交人的成员编号。 */
    private final @Nullable String dataCreator;
    /** 是否发起流程（仅流程表单有效）。 */
    private final @Nullable Boolean startWorkflow;
    /** 是否触发智能助手。 */
    private final @Nullable Boolean startTrigger;
    /** 事务 ID，数据含附件或图片时须与上传凭证使用同一个。 */
    private final @Nullable String transactionId;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private CreateDataRequest(Builder builder) {
        appId = JiandaoyunValidationUtils.requireAppId(builder.appId);
        entryId = JiandaoyunValidationUtils.requireEntryId(builder.entryId);
        data = ValidationUtils.requireNonNull(builder.data, "data is required");
        dataCreator = builder.dataCreator;
        startWorkflow = builder.startWorkflow;
        startTrigger = builder.startTrigger;
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
     * 返回未包装的数据内容。
     *
     * @return 字段名到值的不可变映射
     */
    public Map<String, @Nullable Object> data() {
        return data;
    }

    /**
     * 返回数据提交人。
     *
     * @return 成员编号；未配置时为 {@code null}，由简道云使用企业创建者
     */
    public @Nullable String dataCreator() {
        return dataCreator;
    }

    /**
     * 返回是否发起流程。
     *
     * @return 未配置时为 {@code null}，简道云默认不发起
     */
    public @Nullable Boolean startWorkflow() {
        return startWorkflow;
    }

    /**
     * 返回是否触发智能助手。
     *
     * @return 未配置时为 {@code null}，简道云默认不触发
     */
    public @Nullable Boolean startTrigger() {
        return startTrigger;
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
     * 新建单条数据请求构建器。
     */
    public static final class Builder {
        /** 应用 ID。 */
        private @Nullable String appId;
        /** 表单 ID。 */
        private @Nullable String entryId;
        /** 数据内容。 */
        private @Nullable Map<String, @Nullable Object> data;
        /** 数据提交人。 */
        private @Nullable String dataCreator;
        /** 是否发起流程。 */
        private @Nullable Boolean startWorkflow;
        /** 是否触发智能助手。 */
        private @Nullable Boolean startTrigger;
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
         * 配置数据内容。
         *
         * @param value 字段名到值的映射，值无需包装 {@code {"value": ...}}
         * @return 当前构建器
         */
        public Builder data(Map<String, ?> value) {
            ValidationUtils.requireNonNull(value, "data must not be null");
            data = Collections.unmodifiableMap(new LinkedHashMap<>(value));
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
         * 配置是否触发智能助手。
         *
         * @param value 为 {@code true} 时触发
         * @return 当前构建器
         */
        public Builder startTrigger(boolean value) {
            startTrigger = value;
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
         * @throws IllegalArgumentException 未配置应用 ID、表单 ID 或数据内容时抛出
         */
        public CreateDataRequest build() {
            return new CreateDataRequest(this);
        }
    }
}
