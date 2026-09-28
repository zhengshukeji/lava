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

package com.zhengshuyun.lava.jiandaoyun.application;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 查询应用列表的不可变分页参数。
 *
 * <p>分页参数未配置时省略对应请求字段，由简道云服务端应用默认值（limit=100、skip=0）。</p>
 */
public final class ListApplicationsRequest {
    /** 单次取数条数；未配置时由简道云按 100 处理。 */
    private final @Nullable Integer limit;
    /** 需跳过的数据条数；未配置时从第一条开始。 */
    private final @Nullable Integer skip;

    /**
     * 使用构建期参数创建不可变应用列表请求，并校验分页范围。
     *
     * @param builder 已收集分页参数的构建器
     * @throws IllegalArgumentException 分页参数越界时抛出
     */
    private ListApplicationsRequest(Builder builder) {
        limit = builder.limit;
        skip = builder.skip;
        requirePaging(limit, skip);
    }

    /**
     * 创建应用列表请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回单次取数条数。
     *
     * @return 取数条数；未配置时为 {@code null}，由简道云按 100 处理
     */
    public @Nullable Integer limit() {
        return limit;
    }

    /**
     * 返回需跳过的数据条数。
     *
     * @return 跳过条数；未配置时为 {@code null}，从第一条开始
     */
    public @Nullable Integer skip() {
        return skip;
    }

    /**
     * 校验列表接口通用的分页参数范围。
     *
     * @param limit 单次取数条数
     * @param skip 跳过条数
     */
    private static void requirePaging(@Nullable Integer limit, @Nullable Integer skip) {
        if (limit != null) {
            ValidationUtils.requireTrue(limit >= 1, "limit must be positive");
        }
        if (skip != null) {
            ValidationUtils.requireTrue(skip >= 0, "skip must not be negative");
        }
    }

    /**
     * 应用列表请求构建器。
     */
    public static final class Builder {
        /** 单次取数条数；可选，初始为 {@code null}。 */
        private @Nullable Integer limit;
        /** 需跳过的数据条数；可选，初始为 {@code null}。 */
        private @Nullable Integer skip;

        /** 创建空应用列表请求构建器。 */
        private Builder() {
        }

        /**
         * 配置单次取数条数。
         *
         * @param value 取数条数，范围 1~100
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置需跳过的数据条数。
         *
         * @param value 跳过条数，从 0 开始
         * @return 当前构建器
         */
        public Builder skip(int value) {
            skip = value;
            return this;
        }

        /**
         * 校验并创建应用列表请求。
         *
         * @return 不可变应用列表请求
         * @throws IllegalArgumentException 分页参数越界时抛出
         */
        public ListApplicationsRequest build() {
            return new ListApplicationsRequest(this);
        }
    }
}
