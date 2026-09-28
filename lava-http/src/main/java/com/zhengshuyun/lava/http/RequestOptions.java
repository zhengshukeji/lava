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

package com.zhengshuyun.lava.http;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * 单次请求的可选覆盖项；未设置的值继承客户端配置。
 */
public final class RequestOptions {
    /**
     * 单次请求的连接超时覆盖值。
     */
    private final @Nullable Duration connectTimeout;
    /**
     * 单次请求的读取超时覆盖值。
     */
    private final @Nullable Duration readTimeout;
    /**
     * 单次请求的写入超时覆盖值。
     */
    private final @Nullable Duration writeTimeout;
    /**
     * 单次请求的总调用超时覆盖值。
     */
    private final @Nullable Duration callTimeout;
    /**
     * 单次缓冲响应正文的最大字节数覆盖值。
     */
    private final @Nullable Integer maxBufferedResponseBytes;

    private RequestOptions(Builder builder) {
        connectTimeout = builder.connectTimeout;
        readTimeout = builder.readTimeout;
        writeTimeout = builder.writeTimeout;
        callTimeout = builder.callTimeout;
        maxBufferedResponseBytes = builder.maxBufferedResponseBytes;
    }

    /**
     * 返回不覆盖客户端配置的默认单次请求选项。
     *
     * @return 默认请求选项
     */
    public static RequestOptions defaults() {
        return new Builder().build();
    }

    /**
     * 创建请求选项构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nullable Duration connectTimeout() {
        return connectTimeout;
    }

    @Nullable Duration readTimeout() {
        return readTimeout;
    }

    @Nullable Duration writeTimeout() {
        return writeTimeout;
    }

    @Nullable Duration callTimeout() {
        return callTimeout;
    }

    @Nullable Integer maxBufferedResponseBytes() {
        return maxBufferedResponseBytes;
    }

    boolean isDefault() {
        return connectTimeout == null && readTimeout == null && writeTimeout == null
                && callTimeout == null && maxBufferedResponseBytes == null;
    }

    /**
     * {@link RequestOptions} 的构建器
     */
    public static final class Builder {
        /**
         * 待构建的连接超时。
         */
        private @Nullable Duration connectTimeout;
        /**
         * 待构建的读取超时。
         */
        private @Nullable Duration readTimeout;
        /**
         * 待构建的写入超时。
         */
        private @Nullable Duration writeTimeout;
        /**
         * 待构建的总调用超时。
         */
        private @Nullable Duration callTimeout;
        /**
         * 待构建的缓冲响应上限。
         */
        private @Nullable Integer maxBufferedResponseBytes;

        /**
         * 创建构建器。
         */
        public Builder() {
        }

        /**
         * 设置连接超时覆盖值。
         *
         * @param value 非负超时；零表示不限制
         * @return 当前构建器
         */
        public Builder connectTimeout(Duration value) {
            connectTimeout = requireTimeout(value, "connectTimeout");
            return this;
        }

        /**
         * 设置读取超时覆盖值。
         *
         * @param value 非负超时；零表示不限制
         * @return 当前构建器
         */
        public Builder readTimeout(Duration value) {
            readTimeout = requireTimeout(value, "readTimeout");
            return this;
        }

        /**
         * 设置写入超时覆盖值。
         *
         * @param value 非负超时；零表示不限制
         * @return 当前构建器
         */
        public Builder writeTimeout(Duration value) {
            writeTimeout = requireTimeout(value, "writeTimeout");
            return this;
        }

        /**
         * 设置总调用超时覆盖值。
         *
         * @param value 非负超时；零表示不限制
         * @return 当前构建器
         */
        public Builder callTimeout(Duration value) {
            callTimeout = requireTimeout(value, "callTimeout");
            return this;
        }

        /**
         * 设置缓冲响应正文的最大字节数。
         *
         * @param value 允许的非负最大字节数
         * @return 当前构建器
         */
        public Builder maxBufferedResponseBytes(int value) {
            if (value < 0 || value == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("maxBufferedResponseBytes is out of range");
            }
            maxBufferedResponseBytes = value;
            return this;
        }

        /**
         * 构建不可变请求选项。
         *
         * @return 请求选项
         */
        public RequestOptions build() {
            return new RequestOptions(this);
        }

        private static Duration requireTimeout(Duration value, String name) {
            ValidationUtils.requireNonNull(value, name + " must be non-negative");
            if (value.isNegative()) {
                throw new IllegalArgumentException(name + " must be non-negative");
            }
            return value;
        }
    }
}
