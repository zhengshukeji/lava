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

package com.zhengshuyun.lava.jiandaoyun.member;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云成员信息。
 *
 * <p>数据记录的提交人/修改人、部门成员、角色成员、流程待办人等位置均使用该结构。</p>
 *
 * @param username 成员编号，企业内唯一
 * @param name 成员昵称，可重复
 * @param departments 所属部门编号列表；接口未返回时为 {@code null}
 * @param type 成员类型：0 普通成员，2 企业互联对接人
 * @param status 成员状态：0 未加入，1 已加入，-1 集成模式下已删除
 * @param integrateId 集成模式同步关联 ID，仅集成模式返回
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Member(
        String username,
        @Nullable String name,
        @Nullable List<Long> departments,
        @Nullable Integer type,
        @Nullable Integer status,
        @JsonProperty("integrate_id") @Nullable String integrateId
) {
}
