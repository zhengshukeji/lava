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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 流程待办任务。
 *
 * @param appId 应用 ID
 * @param formId 表单 ID
 * @param formTitle 表单名称
 * @param title 待办名称
 * @param instanceId 流程实例 ID，同数据 ID
 * @param taskId 待办 ID
 * @param flowId 节点 ID
 * @param flowName 节点名称
 * @param url 待办访问链接
 * @param assignee 待办人；待办列表接口不返回
 * @param creator 流程发起人；待办列表接口不返回
 * @param createTime 待办开始时间
 * @param createAction 创建待办的流程动作，如 forward
 * @param finishTime 待办完成时间；未完成时为 {@code null}
 * @param finishAction 完成待办的流程动作；未完成时为 {@code null}
 * @param status 待办状态：0 进行中，1 完成，2 手动结束，4 激活，5 暂停
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowTask(
        @JsonProperty("app_id") String appId,
        @JsonProperty("form_id") String formId,
        @JsonProperty("form_title") @Nullable String formTitle,
        @Nullable String title,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("flow_id") @Nullable Integer flowId,
        @JsonProperty("flow_name") @Nullable String flowName,
        @Nullable String url,
        @Nullable Member assignee,
        @Nullable Member creator,
        @JsonProperty("create_time") @Nullable Instant createTime,
        @JsonProperty("create_action") @Nullable String createAction,
        @JsonProperty("finish_time") @Nullable Instant finishTime,
        @JsonProperty("finish_action") @Nullable String finishAction,
        @Nullable Integer status
) {
}
