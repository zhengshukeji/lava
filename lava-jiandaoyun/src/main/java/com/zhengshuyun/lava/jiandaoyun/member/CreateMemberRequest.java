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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 添加成员的不可变请求参数。
 */
public final class CreateMemberRequest {
    /** 成员昵称。 */
    private final String name;
    /** 成员编号，仅允许字母、数字和下划线；未配置时由简道云生成。 */
    private final @Nullable String username;
    /** 所属部门编号。 */
    private final @Nullable List<Long> departments;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private CreateMemberRequest(Builder builder) {
        name = JiandaoyunValidationUtils.requireNotBlank(builder.name, "name");
        username = builder.username;
        departments = builder.departments;
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
     * 返回成员昵称。
     *
     * @return 昵称
     */
    public String name() {
        return name;
    }

    /**
     * 返回成员编号。
     *
     * @return 成员编号；未配置时为 {@code null}
     */
    public @Nullable String username() {
        return username;
    }

    /**
     * 返回所属部门编号。
     *
     * @return 部门编号列表；未配置时为 {@code null}
     */
    public @Nullable List<Long> departments() {
        return departments;
    }

    /**
     * 添加成员请求构建器。
     */
    public static final class Builder {
        /** 成员昵称。 */
        private @Nullable String name;
        /** 成员编号。 */
        private @Nullable String username;
        /** 所属部门编号。 */
        private @Nullable List<Long> departments;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置成员昵称。
         *
         * @param value 昵称
         * @return 当前构建器
         */
        public Builder name(String value) {
            name = value;
            return this;
        }

        /**
         * 配置成员编号。
         *
         * @param value 成员编号，仅允许字母、数字和下划线
         * @return 当前构建器
         */
        public Builder username(String value) {
            username = JiandaoyunValidationUtils.requireNotBlank(value, "username");
            return this;
        }

        /**
         * 配置所属部门。
         *
         * @param value 部门编号
         * @return 当前构建器
         */
        public Builder departments(Long... value) {
            return departments(List.of(value));
        }

        /**
         * 配置所属部门。
         *
         * @param value 部门编号列表
         * @return 当前构建器
         */
        public Builder departments(List<Long> value) {
            departments = List.copyOf(ValidationUtils.requireNonNull(value, "departments must not be null"));
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置昵称时抛出
         */
        public CreateMemberRequest build() {
            return new CreateMemberRequest(this);
        }
    }
}
