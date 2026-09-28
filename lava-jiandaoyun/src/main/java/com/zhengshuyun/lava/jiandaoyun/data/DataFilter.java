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

package com.zhengshuyun.lava.jiandaoyun.data;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.util.List;

/**
 * 数据查询过滤器，由多个 {@link DataCondition} 以「且」或「或」组合。
 *
 * <pre>{@code
 * DataFilter.and(
 *         DataCondition.eq("_widget_1", "张三"),
 *         DataCondition.range("_widget_2", 10, 20))
 * }</pre>
 *
 * @param rel 条件关系：and 或 or
 * @param cond 条件列表
 */
public record DataFilter(String rel, List<DataCondition> cond) {
    /**
     * 校验并复制条件列表。
     *
     * @param rel 条件关系
     * @param cond 条件列表
     */
    public DataFilter {
        ValidationUtils.requireNotBlank(rel, "rel must not be blank");
        cond = List.copyOf(ValidationUtils.requireNonNull(cond, "cond must not be null"));
    }

    /**
     * 全部条件同时满足。
     *
     * @param conditions 条件
     * @return 过滤器
     */
    public static DataFilter and(DataCondition... conditions) {
        return new DataFilter("and", List.of(conditions));
    }

    /**
     * 全部条件同时满足。
     *
     * @param conditions 条件
     * @return 过滤器
     */
    public static DataFilter and(List<DataCondition> conditions) {
        return new DataFilter("and", conditions);
    }

    /**
     * 任一条件满足。
     *
     * @param conditions 条件
     * @return 过滤器
     */
    public static DataFilter or(DataCondition... conditions) {
        return new DataFilter("or", List.of(conditions));
    }

    /**
     * 任一条件满足。
     *
     * @param conditions 条件
     * @return 过滤器
     */
    public static DataFilter or(List<DataCondition> conditions) {
        return new DataFilter("or", conditions);
    }
}
