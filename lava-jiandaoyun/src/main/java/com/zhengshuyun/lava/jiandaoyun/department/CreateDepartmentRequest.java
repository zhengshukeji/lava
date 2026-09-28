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

import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 创建部门的不可变请求参数。
 */
public final class CreateDepartmentRequest {
    /** 部门名称。 */
    private final String name;
    /** 父部门编号；未配置时为根部门。 */
    private final @Nullable Long parentNo;
    /** 部门编号；未配置时由简道云生成。 */
    private final @Nullable Long deptNo;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private CreateDepartmentRequest(Builder builder) {
        name = JiandaoyunValidationUtils.requireNotBlank(builder.name, "name");
        parentNo = builder.parentNo;
        deptNo = builder.deptNo;
    }

    /**
     * 创建请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回部门名称。
     *
     * @return 部门名称
     */
    public String name() {
        return name;
    }

    /**
     * 返回父部门编号。
     *
     * @return 父部门编号；未配置时为 {@code null}
     */
    public @Nullable Long parentNo() {
        return parentNo;
    }

    /**
     * 返回部门编号。
     *
     * @return 部门编号；未配置时为 {@code null}
     */
    public @Nullable Long deptNo() {
        return deptNo;
    }

    /**
     * 创建部门请求构建器。
     */
    public static final class Builder {
        /** 部门名称。 */
        private @Nullable String name;
        /** 父部门编号。 */
        private @Nullable Long parentNo;
        /** 部门编号。 */
        private @Nullable Long deptNo;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置部门名称。
         *
         * @param value 部门名称，最多 50 个字符
         * @return 当前构建器
         */
        public Builder name(String value) {
            name = value;
            return this;
        }

        /**
         * 配置父部门。
         *
         * @param value 父部门编号
         * @return 当前构建器
         */
        public Builder parentNo(long value) {
            parentNo = value;
            return this;
        }

        /**
         * 指定部门编号。
         *
         * @param value 部门编号，最大 9007199254740991
         * @return 当前构建器
         */
        public Builder deptNo(long value) {
            deptNo = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置部门名称时抛出
         */
        public CreateDepartmentRequest build() {
            return new CreateDepartmentRequest(this);
        }
    }
}
