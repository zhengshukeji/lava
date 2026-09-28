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
 * 流程待办回退的不可变请求参数。
 */
public final class RollbackTaskRequest {
    /** 当前待办的处理人成员编号。 */
    private final String username;
    /** 流程实例 ID。 */
    private final String instanceId;
    /** 待办 ID，必须与处理人一一对应。 */
    private final String taskId;
    /** 回退目标节点 ID，节点配置为「回退到指定节点」时必填。 */
    private final @Nullable Integer flowId;
    /** 回退意见。 */
    private final @Nullable String comment;
    /** 回退人选择，节点配置了「回退人选择」时必填。 */
    private final @Nullable RollbackBackType backType;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private RollbackTaskRequest(Builder builder) {
        username = JiandaoyunValidationUtils.requireNotBlank(builder.username, "username");
        instanceId = JiandaoyunValidationUtils.requireNotBlank(builder.instanceId, "instanceId");
        taskId = JiandaoyunValidationUtils.requireNotBlank(builder.taskId, "taskId");
        flowId = builder.flowId;
        comment = builder.comment;
        backType = builder.backType;
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
     * 返回处理人成员编号。
     *
     * @return 成员编号
     */
    public String username() {
        return username;
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
     * 返回待办 ID。
     *
     * @return 待办 ID
     */
    public String taskId() {
        return taskId;
    }

    /**
     * 返回回退目标节点 ID。
     *
     * @return 节点 ID；未配置时为 {@code null}
     */
    public @Nullable Integer flowId() {
        return flowId;
    }

    /**
     * 返回回退意见。
     *
     * @return 回退意见；未配置时为 {@code null}
     */
    public @Nullable String comment() {
        return comment;
    }

    /**
     * 返回回退人选择。
     *
     * @return 回退人选择；未配置时为 {@code null}
     */
    public @Nullable RollbackBackType backType() {
        return backType;
    }

    /**
     * 待办回退请求构建器。
     */
    public static final class Builder {
        /** 处理人成员编号。 */
        private @Nullable String username;
        /** 流程实例 ID。 */
        private @Nullable String instanceId;
        /** 待办 ID。 */
        private @Nullable String taskId;
        /** 回退目标节点 ID。 */
        private @Nullable Integer flowId;
        /** 回退意见。 */
        private @Nullable String comment;
        /** 回退人选择。 */
        private @Nullable RollbackBackType backType;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置当前待办的处理人。
         *
         * @param value 成员编号
         * @return 当前构建器
         */
        public Builder username(String value) {
            username = value;
            return this;
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
         * 配置待办 ID。
         *
         * @param value 待办 ID
         * @return 当前构建器
         */
        public Builder taskId(String value) {
            taskId = value;
            return this;
        }

        /**
         * 配置回退目标节点。
         *
         * @param value 节点 ID
         * @return 当前构建器
         */
        public Builder flowId(int value) {
            flowId = value;
            return this;
        }

        /**
         * 配置回退意见。
         *
         * @param value 回退意见
         * @return 当前构建器
         */
        public Builder comment(String value) {
            comment = ValidationUtils.requireNonNull(value, "comment must not be null");
            return this;
        }

        /**
         * 配置回退人选择。
         *
         * @param value 回退人选择
         * @return 当前构建器
         */
        public Builder backType(RollbackBackType value) {
            backType = ValidationUtils.requireNonNull(value, "backType must not be null");
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置处理人、实例 ID 或待办 ID 时抛出
         */
        public RollbackTaskRequest build() {
            return new RollbackTaskRequest(this);
        }
    }
}
