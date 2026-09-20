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

package com.zhengshuyun.lava.jiandaoyun.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * 简道云表单字段查询结果。
 *
 * @param widgets 业务字段列表
 * @param sysWidgets 系统字段列表；接口未返回时为 {@code null}
 * @param dataModifyTime 表单内数据最新修改时间（ISO-8601 UTC）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormWidgets(
        List<Widget> widgets,
        @Nullable List<SysWidget> sysWidgets,
        Instant dataModifyTime
) {
    /**
     * 简道云业务字段。
     *
     * <p>注意上游命名与直觉相反：{@code name} 是字段别名（无别名时为字段 ID 的等价标识），
     * 数据读写接口以此定位字段；{@code widgetName} 才是控件 ID 本身。</p>
     *
     * @param label 字段标题
     * @param name 字段名，优先使用别名；数据接口按此名称存取字段值
     * @param widgetName 字段 ID（控件标识）
     * @param type 字段类型，如 text、number、subform 等
     * @param items 子表单控件的子字段列表；仅 type 为 subform 时存在
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Widget(
            String label,
            String name,
            @JsonProperty("widgetName") String widgetName,
            String type,
            @Nullable List<Widget> items
    ) {
    }

    /**
     * 简道云系统字段。
     *
     * @param name 系统字段名，如 flowState、creator、createTime 等
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SysWidget(String name) {
    }
}
