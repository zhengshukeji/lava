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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;

import java.util.List;

/**
 * 增量导入的成员。以 {@code username} 为主键，存在则更新、不存在则新建。
 *
 * @param username 成员编号，仅允许字母、数字和下划线
 * @param name 昵称
 * @param departments 所属部门编号；为空列表时归入根部门
 */
public record ImportMember(String username, String name, List<Long> departments) {
    /**
     * 校验必填项并复制部门列表。
     *
     * @param username 成员编号
     * @param name 昵称
     * @param departments 所属部门编号
     */
    public ImportMember {
        JiandaoyunValidationUtils.requireNotBlank(username, "username");
        JiandaoyunValidationUtils.requireNotBlank(name, "name");
        departments = List.copyOf(ValidationUtils.requireNonNull(departments, "departments must not be null"));
    }
}
