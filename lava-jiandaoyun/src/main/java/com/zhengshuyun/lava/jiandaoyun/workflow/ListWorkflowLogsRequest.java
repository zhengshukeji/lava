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

package com.zhengshuyun.lava.jiandaoyun.workflow;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 查询流程日志的不可变请求参数。
 */
public final class ListWorkflowLogsRequest {
    /** 审批意见日志类型。 */
    public static final String TYPE_COMMENT = "comment";

    /** 流程实例 ID。 */
    private final String instanceId;
    /** 日志类型。 */
    private final List<String> types;
    /** 每页条数，默认 100，最大 100。 */
    private final @Nullable Integer limit;
    /** 跳过条数。 */
    private final @Nullable Integer skip;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private ListWorkflowLogsRequest(Builder builder) {
        instanceId = JiandaoyunValidationUtils.requireNotBlank(builder.instanceId, "instanceId");
        types = builder.types;
        limit = builder.limit;
        skip = builder.skip;
        if (limit != null) {
            ValidationUtils.requireTrue(limit >= 1, "limit must be positive");
        }
        if (skip != null) {
            ValidationUtils.requireTrue(skip >= 0, "skip must not be negative");
        }
    }

    /**
     * 创建请求构建器，日志类型默认为审批意见（{@value #TYPE_COMMENT}）。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回流程实例 ID。
     *
     * @return 流程实例 ID
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 返回日志类型。
     *
     * @return 日志类型列表
     */
    public List<String> types() {
        return types;
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
     * 返回跳过条数。
     *
     * @return 跳过条数；未配置时为 {@code null}
     */
    public @Nullable Integer skip() {
        return skip;
    }

    /**
     * 流程日志请求构建器。
     */
    public static final class Builder {
        /** 流程实例 ID。 */
        private @Nullable String instanceId;
        /** 日志类型，默认审批意见。 */
        private List<String> types = List.of(TYPE_COMMENT);
        /** 每页条数。 */
        private @Nullable Integer limit;
        /** 跳过条数。 */
        private @Nullable Integer skip;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置流程实例 ID（同数据 ID）。
         *
         * @param value 流程实例 ID
         * @return 当前构建器
         */
        public Builder instanceId(String value) {
            instanceId = value;
            return this;
        }

        /**
         * 配置日志类型。
         *
         * @param value 日志类型，如 {@value #TYPE_COMMENT}
         * @return 当前构建器
         */
        public Builder types(String... value) {
            return types(List.of(value));
        }

        /**
         * 配置日志类型。
         *
         * @param value 日志类型列表
         * @return 当前构建器
         */
        public Builder types(List<String> value) {
            types = List.copyOf(ValidationUtils.requireNonNull(value, "types must not be null"));
            return this;
        }

        /**
         * 配置每页条数。
         *
         * @param value 每页条数，范围 1~100
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置跳过条数。
         *
         * @param value 跳过条数，从 0 开始
         * @return 当前构建器
         */
        public Builder skip(int value) {
            skip = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置实例 ID 或分页参数越界时抛出
         */
        public ListWorkflowLogsRequest build() {
            return new ListWorkflowLogsRequest(this);
        }
    }
}
