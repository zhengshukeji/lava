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

import java.time.Duration;
import java.time.Instant;

/**
 * 单次 HTTP 交互的元数据，常用于日志与排障；URL 与请求/响应头在构造时已脱敏。
 *
 * @param requestId       客户端为本次调用生成的唯一标识
 * @param method          HTTP 方法
 * @param url             已脱敏的请求 URL
 * @param requestTime     发出请求的时刻
 * @param responseTime    收到响应头的时刻
 * @param duration        请求耗时，截至收到响应（缓冲响应为读完正文）
 * @param requestHeaders  已脱敏的实际请求头
 * @param responseHeaders 已脱敏的响应头
 * @param protocol        协商的 HTTP 协议
 * @param statusCode      HTTP 状态码
 * @param statusMessage   HTTP 状态文本
 */
public record HttpCallMetadata(
        String requestId,
        String method,
        String url,
        Instant requestTime,
        Instant responseTime,
        Duration duration,
        HttpHeaders requestHeaders,
        HttpHeaders responseHeaders,
        String protocol,
        int statusCode,
        String statusMessage) {

    public HttpCallMetadata {
        ValidationUtils.requireNonNull(requestId, "requestId");
        ValidationUtils.requireNonNull(method, "method");
        url = HttpRedactionUtils.redactUrl(ValidationUtils.requireNonNull(url, "url"));
        ValidationUtils.requireNonNull(requestTime, "requestTime");
        ValidationUtils.requireNonNull(responseTime, "responseTime");
        ValidationUtils.requireNonNull(duration, "duration");
        ValidationUtils.requireTrue(!duration.isNegative(), "duration must not be negative");
        requestHeaders = ValidationUtils.requireNonNull(requestHeaders, "requestHeaders").redacted();
        responseHeaders = ValidationUtils.requireNonNull(responseHeaders, "responseHeaders").redacted();
        ValidationUtils.requireNonNull(protocol, "protocol");
        ValidationUtils.requireNonNull(statusMessage, "statusMessage");
    }

    /**
     * 返回以毫秒计的请求耗时。
     *
     * @return 耗时毫秒数
     */
    public long durationMillis() {
        return duration.toMillis();
    }

    /**
     * 判断状态码是否为 2xx。
     *
     * @return 2xx 时返回 true
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
