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
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * 简道云服务器返回业务失败时抛出的结构化异常。
 *
 * <p>业务失败包括非 2xx 响应，以及流程类接口在 2xx 下返回的 {@code {"status": "failure"}}；
 * 文件上传到对象存储失败时，{@link #code()} 与 HTTP 状态码相同。</p>
 *
 * <p>异常消息只包含 HTTP 状态码和简道云错误码。简道云返回的错误描述通过显式访问器提供，
 * 避免日志框架自动打印异常时泄露业务输入。</p>
 */
public final class JiandaoyunApiException extends JiandaoyunException {
    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 响应状态码。 */
    private final int statusCode;
    /** 简道云 API 错误码。 */
    private final int code;
    /** 简道云返回的错误描述。 */
    private final String apiMessage;

    /**
     * 创建简道云 API 错误。
     *
     * @param statusCode HTTP 状态码
     * @param code 简道云错误码
     * @param apiMessage 简道云错误描述；缺失时使用空字符串
     */
    public JiandaoyunApiException(int statusCode, int code, @Nullable String apiMessage) {
        super(format(statusCode, code));
        // 对象存储（七牛）会返回 614 等非标准三位状态码，因此只约束为三位数
        ValidationUtils.requireTrue(statusCode >= 100 && statusCode <= 999,
                "statusCode must be a valid HTTP status code");
        this.statusCode = statusCode;
        this.code = code;
        this.apiMessage = apiMessage == null ? "" : apiMessage;
    }

    /**
     * 返回 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 返回简道云错误码。
     *
     * @return API 错误码
     */
    public int code() {
        return code;
    }

    /**
     * 返回简道云错误描述。
     *
     * @return API 错误描述，可能包含业务上下文，不应直接写入不受控日志
     */
    public String apiMessage() {
        return apiMessage;
    }

    /**
     * 构造不包含响应业务值的安全异常文本。
     *
     * @param statusCode HTTP 响应状态码
     * @param code 简道云错误码
     * @return 可安全记录的异常消息
     */
    private static String format(int statusCode, int code) {
        return "简道云 API 调用失败: status=" + statusCode + ", code=" + code;
    }
}
