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

package com.zhengshuyun.lava.jiandaoyun.role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * 简道云角色。
 *
 * @param roleNo 角色编号
 * @param groupNo 所属角色组编号
 * @param name 角色名称
 * @param type 角色类型：0 普通角色，2 企业互联外部角色
 * @param status 状态：0 未加入，1 已加入
 * @param integrateId 集成模式同步关联 ID，仅集成模式返回
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Role(
        @JsonProperty("role_no") long roleNo,
        @JsonProperty("group_no") @Nullable Long groupNo,
        @Nullable String name,
        @Nullable Integer type,
        @Nullable Integer status,
        @JsonProperty("integrate_id") @Nullable String integrateId
) {
}
