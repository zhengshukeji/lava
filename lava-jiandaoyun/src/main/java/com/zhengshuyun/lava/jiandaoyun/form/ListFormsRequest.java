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

package com.zhengshuyun.lava.jiandaoyun.form;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 查询表单列表的不可变请求参数。
 *
 * <p>分页参数未配置时省略对应请求字段，由简道云服务端应用默认值（limit=100、skip=0）。</p>
 */
public final class ListFormsRequest {
    /** 应用 ID，查询该应用下的全部表单。 */
    private final String appId;
    /** 单次取数条数；未配置时由简道云按 100 处理。 */
    private final @Nullable Integer limit;
    /** 需跳过的数据条数；未配置时从第一条开始。 */
    private final @Nullable Integer skip;

    /**
     * 使用构建期参数创建不可变表单列表请求，并校验应用 ID 与分页范围。
     *
     * @param builder 已收集请求参数的构建器
     * @throws IllegalArgumentException 未配置应用 ID 或分页参数越界时抛出
     */
    private ListFormsRequest(Builder builder) {
        appId = JiandaoyunValidationUtils.requireAppId(builder.appId);
        limit = builder.limit;
        skip = builder.skip;
        if (limit != null) {
            ValidationUtils.requireTrue(limit >= 1 && limit <= 100,
                    "limit must be between 1 and 100");
        }
        if (skip != null) {
            ValidationUtils.requireTrue(skip >= 0, "skip must not be negative");
        }
    }

    /**
     * 创建表单列表请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回目标应用 ID。
     *
     * @return 应用 ID
     */
    public String appId() {
        return appId;
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
     * 表单列表请求构建器。
     */
    public static final class Builder {
        /** 目标应用 ID；必填，初始为 {@code null}。 */
        private @Nullable String appId;
        /** 单次取数条数；可选，初始为 {@code null}。 */
        private @Nullable Integer limit;
        /** 需跳过的数据条数；可选，初始为 {@code null}。 */
        private @Nullable Integer skip;

        /** 创建空表单列表请求构建器。 */
        private Builder() {
        }

        /**
         * 配置目标应用 ID。
         *
         * @param value 应用 ID
         * @return 当前构建器
         */
        public Builder appId(String value) {
            appId = JiandaoyunValidationUtils.requireAppId(value);
            return this;
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
         * 校验并创建表单列表请求。
         *
         * @return 不可变表单列表请求
         * @throws IllegalArgumentException 未配置应用 ID 或分页参数越界时抛出
         */
        public ListFormsRequest build() {
            return new ListFormsRequest(this);
        }
    }
}
