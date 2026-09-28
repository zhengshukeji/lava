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
 * 流程日志条目。
 *
 * @param flowId 节点 ID
 * @param flowName 节点名称
 * @param createAction 创建该任务的流程动作
 * @param createTime 任务开始时间
 * @param finishAction 完成该任务的流程动作
 * @param finishTime 任务完成时间
 * @param comment 审批意见
 * @param signature 手写签名；未签名时为 {@code null}
 * @param attachments 审批附件
 * @param operator 处理人
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowLog(
        @JsonProperty("flow_id") @Nullable Integer flowId,
        @JsonProperty("flow_name") @Nullable String flowName,
        @JsonProperty("create_action") @Nullable String createAction,
        @JsonProperty("create_time") @Nullable Instant createTime,
        @JsonProperty("finish_action") @Nullable String finishAction,
        @JsonProperty("finish_time") @Nullable Instant finishTime,
        @Nullable String comment,
        @Nullable Signature signature,
        @Nullable List<Attachment> attachments,
        @Nullable Member operator
) {
    /**
     * 手写签名。
     *
     * @param url 签名图片地址，15 天内有效
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Signature(@Nullable String url) {
    }

    /**
     * 审批附件。
     *
     * @param name 文件名
     * @param size 文件大小（字节）
     * @param mime 媒体类型
     * @param url 下载地址，15 天内有效
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(
            @Nullable String name,
            @Nullable Long size,
            @Nullable String mime,
            @Nullable String url
    ) {
    }
}
