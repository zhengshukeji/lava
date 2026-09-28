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

package com.zhengshuyun.lava.jiandaoyun.exception;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpException;
import com.zhengshuyun.lava.http.HttpFailureKind;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * 简道云 HTTP 传输失败。
 */
public final class JiandaoyunTransportException extends JiandaoyunException {
    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 稳定的传输失败类别。 */
    private final HttpFailureKind kind;
    /** 失败请求的 HTTP 方法。 */
    private final @Nullable String method;
    /** 失败请求的已脱敏 URL。 */
    private final @Nullable String url;

    /**
     * 由底层 HTTP 传输失败创建异常，原始 {@link HttpException} 作为 cause 保留。
     *
     * @param cause 底层 HTTP 传输失败
     */
    public JiandaoyunTransportException(HttpException cause) {
        super("简道云传输失败: kind=" + ValidationUtils.requireNonNull(cause, "cause").getKind()
                + (cause.getMethod() == null ? "" : ", method=" + cause.getMethod()), cause);
        this.kind = cause.getKind();
        this.method = cause.getMethod();
        this.url = cause.getUrl();
    }

    /**
     * 返回稳定的传输失败类别。
     *
     * @return 传输失败类别
     */
    public HttpFailureKind kind() {
        return kind;
    }

    /**
     * 返回失败请求的 HTTP 方法。
     *
     * @return HTTP 方法；无法确定时为 {@code null}
     */
    public @Nullable String method() {
        return method;
    }

    /**
     * 返回失败请求的已脱敏 URL。
     *
     * @return 已脱敏 URL；无法确定时为 {@code null}
     */
    public @Nullable String url() {
        return url;
    }

}
