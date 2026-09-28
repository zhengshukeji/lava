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

/**
 * 查询成员待办的不可变请求参数。
 */
public final class ListWorkflowTasksRequest {
    /** 成员编号。 */
    private final String username;
    /** 翻页游标：上一页最后一个待办 ID。 */
    private final @Nullable String taskId;
    /** 每页条数，默认 10，最大 100。 */
    private final @Nullable Integer limit;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private ListWorkflowTasksRequest(Builder builder) {
        username = JiandaoyunValidationUtils.requireNotBlank(builder.username, "username");
        taskId = builder.taskId;
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
     * 返回成员编号。
     *
     * @return 成员编号
     */
    public String username() {
        return username;
    }

    /**
     * 返回翻页游标。
     *
     * @return 上一页最后一个待办 ID；未配置时为 {@code null}
     */
    public @Nullable String taskId() {
        return taskId;
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
     * 待办查询请求构建器。
     */
    public static final class Builder {
        /** 成员编号。 */
        private @Nullable String username;
        /** 翻页游标。 */
        private @Nullable String taskId;
        /** 每页条数。 */
        private @Nullable Integer limit;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置成员编号。
         *
         * @param value 成员编号
         * @return 当前构建器
         */
        public Builder username(String value) {
            username = value;
            return this;
        }

        /**
         * 配置翻页游标。
         *
         * @param value 上一页最后一个待办 ID
         * @return 当前构建器
         */
        public Builder taskId(String value) {
            taskId = JiandaoyunValidationUtils.requireNotBlank(value, "taskId");
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
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置成员编号或 limit 非正数时抛出
         */
        public ListWorkflowTasksRequest build() {
            return new ListWorkflowTasksRequest(this);
        }
    }
}
