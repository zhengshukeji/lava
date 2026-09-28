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

package com.zhengshuyun.lava.jiandaoyun.usage;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * 查询成员资源用量的不可变请求参数；未配置的参数由简道云应用默认值。
 */
public final class ListMemberUsageRequest {
    /** 统计日期，默认前一天，最多回溯 180 天。 */
    private final @Nullable LocalDate date;
    /** 成员 ID，最多 10 个；未配置时统计全部。 */
    private final @Nullable List<String> memberIds;
    /** 跳过条数。 */
    private final @Nullable Integer skip;
    /** 每页条数，默认 20，最大 100。 */
    private final @Nullable Integer limit;

    /**
     * 使用构建期参数创建请求并校验分页范围。
     *
     * @param builder 构建器
     */
    private ListMemberUsageRequest(Builder builder) {
        date = builder.date;
        memberIds = builder.memberIds;
        skip = builder.skip;
        limit = builder.limit;
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
     * 返回统计日期。
     *
     * @return 统计日期；未配置时为 {@code null}
     */
    public @Nullable LocalDate date() {
        return date;
    }

    /**
     * 返回成员 ID 过滤。
     *
     * @return 成员 ID 列表；未配置时为 {@code null}
     */
    public @Nullable List<String> memberIds() {
        return memberIds;
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
     * 成员资源用量请求构建器。
     */
    public static final class Builder {
        /** 统计日期。 */
        private @Nullable LocalDate date;
        /** 成员 ID 过滤。 */
        private @Nullable List<String> memberIds;
        /** 跳过条数。 */
        private @Nullable Integer skip;
        /** 每页条数。 */
        private @Nullable Integer limit;

        /** 创建空构建器。 */
        private Builder() {
        }

        /**
         * 配置统计日期。
         *
         * @param value 统计日期，不晚于前一天
         * @return 当前构建器
         */
        public Builder date(LocalDate value) {
            date = ValidationUtils.requireNonNull(value, "date must not be null");
            return this;
        }

        /**
         * 只统计指定成员。
         *
         * @param value 成员 ID，最多 10 个
         * @return 当前构建器
         */
        public Builder memberIds(String... value) {
            return memberIds(List.of(value));
        }

        /**
         * 只统计指定成员。
         *
         * @param value 成员 ID 列表，最多 10 个
         * @return 当前构建器
         */
        public Builder memberIds(List<String> value) {
            memberIds = List.copyOf(ValidationUtils.requireNonNull(value, "memberIds must not be null"));
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
         * 校验并创建请求。
         *
         * @return 不可变请求
         * @throws IllegalArgumentException 分页参数越界时抛出
         */
        public ListMemberUsageRequest build() {
            return new ListMemberUsageRequest(this);
        }
    }
}
