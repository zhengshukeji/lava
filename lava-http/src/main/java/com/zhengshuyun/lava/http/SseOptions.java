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
 * SSE 专用选项；SSE 默认不使用总调用超时。
 */
public final class SseOptions {
    /**
     * 默认允许两分钟没有事件到达。
     */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 两个事件之间允许的最大空闲时间。
     */
    private final Duration idleTimeout;
    /**
     * 发送给服务端、用于恢复事件流的位置游标。
     */
    private final @Nullable String lastEventId;

    private SseOptions(Builder builder) {
        idleTimeout = builder.idleTimeout;
        lastEventId = builder.lastEventId;
    }

    /**
     * 创建 SSE 选项构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建使用默认空闲超时的 SSE 选项。
     *
     * @return 默认 SSE 选项
     */
    public static SseOptions defaults() {
        return builder().build();
    }

    /**
     * 返回 SSE 读取空闲超时。
     *
     * @return 非负超时；零表示不限制
     */
    public Duration idleTimeout() {
        return idleTimeout;
    }

    /**
     * 返回事件流恢复游标。
     *
     * @return Last-Event-ID 值；未设置时返回 null
     */
    public @Nullable String lastEventId() {
        return lastEventId;
    }

    /**
     * {@link SseOptions} 的构建器
     */
    public static final class Builder {
        /**
         * 待构建的读取空闲超时。
         */
        private Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;
        /**
         * 待构建的事件恢复游标。
         */
        private @Nullable String lastEventId;

        /**
         * 创建构建器。
         */
        public Builder() {
        }

        /**
         * 设置两个 SSE 事件之间允许的最大间隔。
         *
         * @param value 非负空闲超时；零表示不限制
         * @return 当前构建器
         */
        public Builder idleTimeout(Duration value) {
            ValidationUtils.requireNonNull(value, "idleTimeout must be non-negative");
            if (value.isNegative()) {
                throw new IllegalArgumentException("idleTimeout must be non-negative");
            }
            idleTimeout = value;
            return this;
        }

        /**
         * 设置发送至服务端的 Last-Event-ID 请求头值。
         *
         * @param value 事件恢复游标；null 表示不发送
         * @return 当前构建器
         */
        public Builder lastEventId(@Nullable String value) {
            lastEventId = value;
            return this;
        }

        /**
         * 构建不可变 SSE 选项。
         *
         * @return SSE 选项
         */
        public SseOptions build() {
            return new SseOptions(this);
        }
    }
}
