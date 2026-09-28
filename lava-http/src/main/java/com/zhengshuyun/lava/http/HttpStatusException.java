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

/**
 * 调用方显式要求成功但服务端返回非 2xx 时抛出的异常。
 */
public final class HttpStatusException extends RuntimeException {
    private static final int MAX_ERROR_BODY_BYTES = 64 * 1024;
    private final int statusCode;
    private final HttpHeaders headers;
    private final String responseBody;

    HttpStatusException(HttpResponse response) {
        super("HTTP status " + response.statusCode() + " from " + response.metadata().url());
        statusCode = response.statusCode();
        headers = response.headers().redacted();
        byte[] body = response.bodyBytes();
        int length = Math.min(body.length, MAX_ERROR_BODY_BYTES);
        responseBody = new String(body, 0, length, response.charset());
    }

    public int statusCode() {
        return statusCode;
    }

    public HttpHeaders headers() {
        return headers;
    }

    /**
     * 返回有界响应正文；异常的 toString 不会包含正文。
     */
    public String responseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return "HttpStatusException[statusCode=" + statusCode
                + ", headers=" + headers + ", responseBody=[REDACTED]]";
    }
}
