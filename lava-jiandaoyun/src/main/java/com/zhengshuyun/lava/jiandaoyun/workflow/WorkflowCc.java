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
 * 流程抄送记录。
 *
 * @param appId 应用 ID
 * @param formId 表单 ID
 * @param taskId 抄送任务 ID
 * @param instanceId 流程实例 ID，同数据 ID
 * @param formTitle 表单名称
 * @param flowId 节点 ID
 * @param flowName 节点名称
 * @param url 访问链接
 * @param assignee 抄送人
 * @param creator 流程发起人
 * @param createTime 流程发起时间
 * @param status 阅读状态：0 未读，1 已读
 * @param startTime 抄送时间
 * @param finishTime 阅读时间；未读时为 {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowCc(
        @JsonProperty("app_id") String appId,
        @JsonProperty("form_id") String formId,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("form_title") @Nullable String formTitle,
        @JsonProperty("flow_id") @Nullable Integer flowId,
        @JsonProperty("flow_name") @Nullable String flowName,
        @Nullable String url,
        @Nullable Member assignee,
        @Nullable Member creator,
        @JsonProperty("create_time") @Nullable Instant createTime,
        @Nullable Integer status,
        @JsonProperty("start_time") @Nullable Instant startTime,
        @JsonProperty("finish_time") @Nullable Instant finishTime
) {
}
