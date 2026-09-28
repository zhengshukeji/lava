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

package com.zhengshuyun.lava.jiandaoyun.workflow;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 查询成员抄送列表的不可变请求参数，仅支持查询 90 天内的抄送。
 */
public final class ListCcRequest {
    /** 成员编号。 */
    private final String username;
    /** 跳过条数。 */
    private final @Nullable Integer skip;
    /** 每页条数，默认 10，最大 100。 */
    private final @Nullable Integer limit;
    /** 阅读状态过滤，默认全部。 */
    private final @Nullable CcReadStatus readStatus;

    /**
     * 使用构建期参数创建请求并校验必填项。
     *
     * @param builder 构建器
     */
    private ListCcRequest(Builder builder) {
        username = JiandaoyunValidationUtils.requireNotBlank(builder.username, "username");
        skip = builder.skip;
        limit = builder.limit;
        readStatus = builder.readStatus;
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
     * 返回成员编号。
     *
     * @return 成员编号
     */
    public String username() {
        return username;
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
     * 返回阅读状态过滤。
     *
     * @return 阅读状态；未配置时为 {@code null}，查询全部
     */
    public @Nullable CcReadStatus readStatus() {
        return readStatus;
    }

    /**
     * 抄送列表请求构建器。
     */
    public static final class Builder {
        /** 成员编号。 */
        private @Nullable String username;
        /** 跳过条数。 */
        private @Nullable Integer skip;
        /** 每页条数。 */
        private @Nullable Integer limit;
        /** 阅读状态。 */
        private @Nullable CcReadStatus readStatus;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置成员编号。
         *
         * @param value 成员编号
         * @return 当前构建器
         */
        public Builder username(String value) {
            username = value;
            return this;
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
         * @param value 每页条数，范围 1~100
         * @return 当前构建器
         */
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /**
         * 配置阅读状态过滤。
         *
         * @param value 阅读状态
         * @return 当前构建器
         */
        public Builder readStatus(CcReadStatus value) {
            readStatus = ValidationUtils.requireNonNull(value, "readStatus must not be null");
            return this;
        }

        /**
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 未配置成员编号或分页参数越界时抛出
         */
        public ListCcRequest build() {
            return new ListCcRequest(this);
        }
    }
}
