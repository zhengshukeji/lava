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
 * 列出角色的不可变请求参数；未配置的参数由简道云应用默认值。
 */
public final class ListRolesRequest {
    /** 跳过条数。 */
    private final @Nullable Integer skip;
    /** 每页条数。 */
    private final @Nullable Integer limit;
    /** 是否包含自建角色，默认包含。 */
    private final @Nullable Boolean hasInternal;
    /** 是否包含集成同步角色，默认包含。 */
    private final @Nullable Boolean hasSync;

    /**
     * 使用构建期参数创建请求并校验分页范围。
     *
     * @param builder 构建器
     */
    private ListRolesRequest(Builder builder) {
        skip = builder.skip;
        limit = builder.limit;
        hasInternal = builder.hasInternal;
        hasSync = builder.hasSync;
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
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
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
     * 返回是否包含自建角色。
     *
     * @return 未配置时为 {@code null}
     */
    public @Nullable Boolean hasInternal() {
        return hasInternal;
    }

    /**
     * 返回是否包含集成同步角色。
     *
     * @return 未配置时为 {@code null}
     */
    public @Nullable Boolean hasSync() {
        return hasSync;
    }

    /**
     * 列出角色请求构建器。
     */
    public static final class Builder {
        /** 跳过条数。 */
        private @Nullable Integer skip;
        /** 每页条数。 */
        private @Nullable Integer limit;
        /** 是否包含自建角色。 */
        private @Nullable Boolean hasInternal;
        /** 是否包含集成同步角色。 */
        private @Nullable Boolean hasSync;

        /** 创建空构建器。 */
        private Builder() {
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
         * @param value 每页条数
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置是否包含自建角色。
         *
         * @param value 为 {@code false} 时排除自建角色
         * @return 当前构建器
         */
        public Builder hasInternal(boolean value) {
            hasInternal = value;
            return this;
        }

        /**
         * 配置是否包含集成同步角色。
         *
         * @param value 为 {@code false} 时排除集成同步角色
         * @return 当前构建器
         */
        public Builder hasSync(boolean value) {
            hasSync = value;
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 分页参数越界时抛出
         */
        public ListRolesRequest build() {
            return new ListRolesRequest(this);
        }
    }
}
