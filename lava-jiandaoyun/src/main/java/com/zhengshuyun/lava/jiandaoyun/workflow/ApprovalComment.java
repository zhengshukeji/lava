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

/**
 * 流程审批意见。
 *
 * @param flowNodeName 节点名称
 * @param flowAction 流程动作：forward 提交、transfer 转交、back 回退、close 结束、
 *                   sign_before 前加签、sign_after 后加签、sign_parallel 并行加签
 * @param comment 审批意见
 * @param signatureUrl 手写签名地址，15 天内有效
 * @param operator 审批人
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalComment(
        @Nullable String flowNodeName,
        @Nullable String flowAction,
        @Nullable String comment,
        @JsonProperty("signature_url") @Nullable String signatureUrl,
        @Nullable Member operator
) {
}
