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

package com.zhengshuyun.lava.jiandaoyun.department;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * 简道云部门信息。
 *
 * @param deptNo 部门编号
 * @param name 部门名称
 * @param parentNo 父部门编号；根部门为 {@code null}
 * @param type 部门类型：0 普通部门，2 企业互联外部部门
 * @param status 部门状态：1 正常，-1 集成模式下已删除
 * @param integrateId 集成模式同步关联 ID，仅集成模式返回
 * @param seq 部门排序
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Department(
        @JsonProperty("dept_no") long deptNo,
        @Nullable String name,
        @JsonProperty("parent_no") @Nullable Long parentNo,
        @Nullable Integer type,
        @Nullable Integer status,
        @JsonProperty("integrate_id") @Nullable String integrateId,
        @Nullable Long seq
) {
}
