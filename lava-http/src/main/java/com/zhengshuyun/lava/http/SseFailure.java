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

/**
 * SSE 会话失败详情。
 *
 * @param kind         稳定的失败分类
 * @param cause        原始异常；服务端返回非 2xx 等协议级失败时为 null
 * @param statusCode   服务端已响应时的 HTTP 状态码
 * @param headers      服务端已响应时的响应头
 * @param responseBody 有界的失败响应正文
 */
public record SseFailure(HttpFailureKind kind, @Nullable Throwable cause,
                         @Nullable Integer statusCode, @Nullable HttpHeaders headers,
                         @Nullable String responseBody) {
    public SseFailure {
        ValidationUtils.requireNonNull(kind, "kind must not be null");
    }

    /**
     * 返回不含响应正文的调试表示，响应头按 {@link HttpHeaders#toString()} 脱敏。
     *
     * @return 失败摘要
     */
    @Override
    public String toString() {
        return "SseFailure[kind=" + kind
                + ", cause=" + (cause == null ? null : cause.getClass().getName())
                + ", statusCode=" + statusCode + ", headers=" + headers
                + ", responseBody=" + (responseBody == null ? null : "[REDACTED]") + ']';
    }
}
