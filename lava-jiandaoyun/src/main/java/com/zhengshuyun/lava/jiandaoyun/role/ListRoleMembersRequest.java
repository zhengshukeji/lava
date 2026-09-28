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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 列出角色下成员的不可变请求参数。
 */
public final class ListRoleMembersRequest {
    /** 角色编号。 */
    private final long roleNo;
    /** 跳过条数。 */
    private final @Nullable Integer skip;
    /** 每页条数，最大 10000。 */
    private final @Nullable Integer limit;
    /** 是否返回分管部门范围，默认不返回。 */
    private final @Nullable Boolean hasManageRange;

    /**
     * 使用构建期参数创建请求并校验分页范围。
     *
     * @param builder 构建器
     */
    private ListRoleMembersRequest(Builder builder) {
        roleNo = builder.roleNo;
        skip = builder.skip;
        limit = builder.limit;
        hasManageRange = builder.hasManageRange;
        if (limit != null) {
            ValidationUtils.requireTrue(limit >= 1, "limit must be positive");
        }
        if (skip != null) {
            ValidationUtils.requireTrue(skip >= 0, "skip must not be negative");
        }
    }

    /**
     * 创建请求构建器。
     *
     * @param roleNo 角色编号
     * @return 新构建器
     */
    public static Builder builder(long roleNo) {
        return new Builder(roleNo);
    }

    /**
     * 返回角色编号。
     *
     * @return 角色编号
     */
    public long roleNo() {
        return roleNo;
    }

    /**
     * 返回跳过条数。
     *
     * @return 跳过条数；未配置时为 {@code null}
     */
    public @Nullable Integer skip() {
        return skip;
    }

    /**
     * 返回每页条数。
     *
     * @return 每页条数；未配置时为 {@code null}
     */
    public @Nullable Integer limit() {
        return limit;
    }

    /**
     * 返回是否返回分管部门范围。
     *
     * @return 未配置时为 {@code null}
     */
    public @Nullable Boolean hasManageRange() {
        return hasManageRange;
    }

    /**
     * 角色成员请求构建器。
     */
    public static final class Builder {
        /** 角色编号。 */
        private final long roleNo;
        /** 跳过条数。 */
        private @Nullable Integer skip;
        /** 每页条数。 */
        private @Nullable Integer limit;
        /** 是否返回分管部门范围。 */
        private @Nullable Boolean hasManageRange;

        /**
         * 创建指定角色的构建器。
         *
         * @param roleNo 角色编号
         */
        private Builder(long roleNo) {
            this.roleNo = roleNo;
        }

        /**
         * 配置跳过条数。
         *
         * @param value 跳过条数，从 0 开始
         * @return 当前构建器
         */
        public Builder skip(int value) {
            skip = value;
            return this;
        }

        /**
         * 配置每页条数。
         *
         * @param value 每页条数，最大 10000
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置是否返回分管部门范围。
         *
         * @param value 为 {@code true} 时返回
         * @return 当前构建器
         */
        public Builder hasManageRange(boolean value) {
            hasManageRange = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 分页参数越界时抛出
         */
        public ListRoleMembersRequest build() {
            return new ListRoleMembersRequest(this);
        }
    }
}
