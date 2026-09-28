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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 应用资源用量分页结果。
 *
 * @param date 统计日期
 * @param hasNext 是否还有下一页
 * @param items 本页应用用量
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppUsagePage(
        LocalDate date,
        @JsonProperty("has_next") boolean hasNext,
        List<Item> items
) {
    /**
     * 单个应用的资源用量。
     *
     * @param appId 应用 ID
     * @param appName 应用名称
     * @param creator 应用创建者
     * @param createdAt 创建时间
     * @param lastEditAt 最后编辑时间
     * @param lastVisitAt 最后访问时间
     * @param metrics 用量指标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("app_id") String appId,
            @JsonProperty("app_name") @Nullable String appName,
            @Nullable UsageMember creator,
            @JsonProperty("created_at") @Nullable OffsetDateTime createdAt,
            @JsonProperty("last_edit_at") @Nullable OffsetDateTime lastEditAt,
            @JsonProperty("last_visit_at") @Nullable OffsetDateTime lastVisitAt,
            UsageMetrics metrics
    ) {
    }
}
