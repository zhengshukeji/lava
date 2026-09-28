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

import java.time.LocalDate;
import java.util.List;

/**
 * 成员资源用量分页结果。
 *
 * @param date 统计日期
 * @param hasNext 是否还有下一页
 * @param items 本页成员用量
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberUsagePage(
        LocalDate date,
        @JsonProperty("has_next") boolean hasNext,
        List<Item> items
) {
    /**
     * 单个成员创建的资源用量。
     *
     * @param member 成员
     * @param metrics 用量指标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(UsageMember member, UsageMetrics metrics) {
    }
}
