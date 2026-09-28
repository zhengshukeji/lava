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

import java.util.List;

/**
 * 角色下的成员，可附带角色分管范围。
 *
 * @param username 成员编号
 * @param name 成员昵称
 * @param departments 所属部门编号
 * @param type 成员类型：0 普通成员，2 企业互联对接人
 * @param status 成员状态：0 未加入，1 已加入
 * @param integrateId 集成模式同步关联 ID，仅集成模式返回
 * @param departmentsRange 角色分管的部门编号；未要求返回分管范围时为 {@code null}
 * @param hasChild 分管范围是否包含子部门
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleMember(
        String username,
        @Nullable String name,
        @Nullable List<Long> departments,
        @Nullable Integer type,
        @Nullable Integer status,
        @JsonProperty("integrate_id") @Nullable String integrateId,
        @JsonProperty("departments_range") @Nullable List<Long> departmentsRange,
        @JsonProperty("has_child") @Nullable Boolean hasChild
) {
}
