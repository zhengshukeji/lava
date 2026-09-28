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

import org.jspecify.annotations.Nullable;

/**
 * HTTP 传输或响应缓冲失败；消息与 {@link #getUrl()} 中的 URL 已脱敏，原始异常作为 cause 保留。
 */
public final class HttpException extends RuntimeException {
    /**
     * 供调用方稳定判断的失败分类。
     */
    private final HttpFailureKind kind;
    /**
     * 出错请求的方法；无法确定时为 null。
     */
    private final @Nullable String method;
    /**
     * 已脱敏的出错请求 URL；无法确定时为 null。
     */
    private final @Nullable String url;

    HttpException(HttpFailureKind kind, @Nullable String method, @Nullable String rawUrl,
                  String detail, @Nullable Throwable cause) {
        super(format(kind, method, rawUrl, detail), cause);
        this.kind = kind;
        this.method = method;
        this.url = rawUrl == null ? null : HttpRedactionUtils.redactUrl(rawUrl);
    }

    /**
     * 返回稳定的失败分类。
     *
     * @return 失败分类
     */
    public HttpFailureKind getKind() {
        return kind;
    }

    /**
     * 返回出错请求的方法。
     *
     * @return HTTP 方法；无法确定时为 null
     */
    public @Nullable String getMethod() {
        return method;
    }

    /**
     * 返回已脱敏用户信息和敏感查询参数的 URL。
     *
     * @return 脱敏 URL；无法确定时为 null
     */
    public @Nullable String getUrl() {
        return url;
    }

    private static String format(HttpFailureKind kind, @Nullable String method,
                                 @Nullable String rawUrl, String detail) {
        StringBuilder result = new StringBuilder("HTTP ").append(kind);
        if (method != null) {
            result.append(" during ").append(method);
        }
        if (rawUrl != null) {
            result.append(' ').append(HttpRedactionUtils.redactUrl(rawUrl));
        }
        if (!detail.isBlank()) {
            result.append(": ").append(detail);
        }
        return result.toString();
    }
}
