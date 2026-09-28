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

package com.zhengshuyun.lava.jiandaoyun.usage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * 资源用量指标。不同统计维度返回的指标不完全相同，未返回的为 {@code null}。
 *
 * @param app 应用数
 * @param formCoop 普通表单数
 * @param formWorkflow 流程表单数
 * @param dash 仪表盘数
 * @param etl 数据工厂数
 * @param aggregate 聚合表数
 * @param publicLink 外链开启数
 * @param dataTrigger 智能助手数
 * @param automation 智能助手 Pro 数
 * @param bpa 流程分析数
 * @param data 数据总量
 * @param dailyAutomationExec 当日智能助手 Pro 执行次数
 * @param dailyAttachmentUpload 当日附件上传量（字节）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageMetrics(
        @Nullable Long app,
        @JsonProperty("form_coop") @Nullable Long formCoop,
        @JsonProperty("form_workflow") @Nullable Long formWorkflow,
        @Nullable Long dash,
        @Nullable Long etl,
        @Nullable Long aggregate,
        @JsonProperty("public_link") @Nullable Long publicLink,
        @JsonProperty("data_trigger") @Nullable Long dataTrigger,
        @Nullable Long automation,
        @Nullable Long bpa,
        @Nullable Long data,
        @JsonProperty("daily_automation_exec") @Nullable Long dailyAutomationExec,
        @JsonProperty("daily_attachment_upload") @Nullable Long dailyAttachmentUpload
) {
}
