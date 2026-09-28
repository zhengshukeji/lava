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
import java.util.List;

/**
 * 流程实例信息。
 *
 * @param appId 应用 ID
 * @param formId 表单 ID
 * @param formTitle 表单名称
 * @param instanceId 流程实例 ID，同数据 ID
 * @param url 实例访问链接
 * @param createTime 创建时间
 * @param updateTime 修改时间
 * @param finishTime 结束时间；未结束时为 {@code null}
 * @param status 实例状态：0 进行中，1 完成，2 手动结束
 * @param result 审批结果：0 否决，1 流转完成；进行中时为 {@code null}
 * @param creator 发起人
 * @param tasks 待办任务；查询时未要求返回任务则为 {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowInstance(
        @JsonProperty("app_id") String appId,
        @JsonProperty("form_id") String formId,
        @JsonProperty("form_title") @Nullable String formTitle,
        @JsonProperty("instance_id") String instanceId,
        @Nullable String url,
        @JsonProperty("create_time") @Nullable Instant createTime,
        @JsonProperty("update_time") @Nullable Instant updateTime,
        @JsonProperty("finish_time") @Nullable Instant finishTime,
        @Nullable Integer status,
        @Nullable Integer result,
        @Nullable Member creator,
        @Nullable List<WorkflowTask> tasks
) {
}
