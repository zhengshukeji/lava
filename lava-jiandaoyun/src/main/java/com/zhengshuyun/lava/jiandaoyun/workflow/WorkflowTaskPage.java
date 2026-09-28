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

import java.util.List;

/**
 * 待办任务分页结果。翻页时把最后一个待办的 {@code taskId} 作为下一页游标。
 *
 * @param hasMore 是否还有下一页
 * @param tasks 本页待办
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowTaskPage(@JsonProperty("has_more") boolean hasMore, List<WorkflowTask> tasks) {
}
