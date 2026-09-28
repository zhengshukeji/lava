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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 全量导入的部门。以 {@code deptNo} 为主键全量覆盖。
 *
 * @param deptNo 部门编号，最大 9007199254740991
 * @param name 部门名称，同级不可重名
 * @param parentNo 父部门编号；为 {@code null} 时归入根部门
 */
public record ImportDepartment(
        @JsonProperty("dept_no") long deptNo,
        String name,
        @JsonProperty("parent_no") @Nullable Long parentNo
) {
    /**
     * 校验部门名称。
     *
     * @param deptNo 部门编号
     * @param name 部门名称
     * @param parentNo 父部门编号
     */
    public ImportDepartment {
        JiandaoyunValidationUtils.requireNotBlank(name, "name");
    }
}
