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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 修改部门的不可变请求参数；名称、父部门、排序至少配置一项。
 */
public final class UpdateDepartmentRequest {
    /** 部门编号。 */
    private final long deptNo;
    /** 新名称。 */
    private final @Nullable String name;
    /** 新的父部门编号。 */
    private final @Nullable Long parentNo;
    /** 新的排序。 */
    private final @Nullable Long seq;

    /**
     * 使用构建期参数创建请求。
     *
     * @param builder 构建器
     */
    private UpdateDepartmentRequest(Builder builder) {
        deptNo = builder.deptNo;
        name = builder.name;
        parentNo = builder.parentNo;
        seq = builder.seq;
        ValidationUtils.requireTrue(name != null || parentNo != null || seq != null,
                "at least one of name, parentNo or seq is required");
    }

    /**
     * 创建请求构建器。
     *
     * @param deptNo 要修改的部门编号
     * @return 新构建器
     */
    public static Builder builder(long deptNo) {
        return new Builder(deptNo);
    }

    /**
     * 返回部门编号。
     *
     * @return 部门编号
     */
    public long deptNo() {
        return deptNo;
    }

    /**
     * 返回新名称。
     *
     * @return 名称；未配置时为 {@code null}
     */
    public @Nullable String name() {
        return name;
    }

    /**
     * 返回新的父部门编号。
     *
     * @return 父部门编号；未配置时为 {@code null}
     */
    public @Nullable Long parentNo() {
        return parentNo;
    }

    /**
     * 返回新的排序。
     *
     * @return 排序；未配置时为 {@code null}
     */
    public @Nullable Long seq() {
        return seq;
    }

    /**
     * 修改部门请求构建器。
     */
    public static final class Builder {
        /** 部门编号。 */
        private final long deptNo;
        /** 新名称。 */
        private @Nullable String name;
        /** 新的父部门编号。 */
        private @Nullable Long parentNo;
        /** 新的排序。 */
        private @Nullable Long seq;

        /**
         * 创建指定部门的构建器。
         *
         * @param deptNo 部门编号
         */
        private Builder(long deptNo) {
            this.deptNo = deptNo;
        }

        /**
         * 配置新名称。
         *
         * @param value 部门名称
         * @return 当前构建器
         */
        public Builder name(String value) {
            name = JiandaoyunValidationUtils.requireNotBlank(value, "name");
            return this;
        }

        /**
         * 配置新的父部门。
         *
         * @param value 父部门编号
         * @return 当前构建器
         */
        public Builder parentNo(long value) {
            parentNo = value;
            return this;
        }

        /**
         * 配置新的排序。
         *
         * @param value 排序值
         * @return 当前构建器
         */
        public Builder seq(long value) {
            seq = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 名称、父部门、排序均未配置时抛出
         */
        public UpdateDepartmentRequest build() {
            return new UpdateDepartmentRequest(this);
        }
    }
}
