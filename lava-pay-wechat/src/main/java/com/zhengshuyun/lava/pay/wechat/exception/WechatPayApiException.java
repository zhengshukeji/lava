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

package com.zhengshuyun.lava.pay.wechat.exception;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.io.Serial;

/**
 * 微信支付服务器返回非成功 HTTP 状态时抛出的结构化异常。
 *
 * <p>异常消息只包含状态码、错误码和 Request-ID。微信返回的描述和错误值通过显式访问器提供，
 * 避免日志框架自动打印异常时泄露业务输入。</p>
 *
 * <p>{@link #verified()} 表示错误正文是否通过微信支付响应签名验证。微信支付网关层错误（如请求签名错误
 * 返回的 401 {@code SIGN_ERROR}）不携带签名，调用方只能将未验签错误用于诊断和重试分类，
 * 不能据此更新支付业务状态。</p>
 */
public final class WechatPayApiException extends WechatPayException {
    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 响应状态码。 */
    private final int statusCode;
    /** 错误正文是否通过微信支付响应签名验证。 */
    private final boolean verified;
    /** 微信支付 API 错误码。 */
    private final String code;
    /** 微信支付返回的错误描述。 */
    private final String apiMessage;
    /** 参数错误详情。 */
    private final @Nullable WechatPayApiErrorDetail detail;
    /** 微信支付服务端请求标识。 */
    private final @Nullable String requestId;

    /**
     * 创建微信支付 API 错误。
     *
     * @param statusCode HTTP 状态码
     * @param verified 错误正文是否通过响应签名验证
     * @param code 微信支付错误码
     * @param apiMessage 微信支付错误描述
     * @param detail 可选参数错误详情
     * @param requestId 可选微信支付 Request-ID
     */
    public WechatPayApiException(
            int statusCode,
            boolean verified,
            String code,
            String apiMessage,
            @Nullable WechatPayApiErrorDetail detail,
            @Nullable String requestId
    ) {
        super(format(statusCode, verified, code, requestId));
        ValidationUtils.requireTrue(statusCode >= 100 && statusCode <= 599,
                "statusCode must be a valid HTTP status code");
        this.statusCode = statusCode;
        this.verified = verified;
        this.code = ValidationUtils.requireNotBlank(code, "code must not be blank");
        this.apiMessage = ValidationUtils.requireNonNull(apiMessage,
                "apiMessage must not be null");
        this.detail = detail;
        this.requestId = requestId;
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
     * 返回错误正文是否通过微信支付响应签名验证。
     *
     * @return 已验证时返回 {@code true}
     */
    public boolean verified() {
        return verified;
    }

    /**
     * 返回微信支付错误码。
     *
     * @return API 错误码
     */
    public String code() {
        return code;
    }

    /**
     * 返回微信支付错误描述。
     *
     * @return API 错误描述，可能包含业务上下文，不应直接写入不受控日志
     */
    public String apiMessage() {
        return apiMessage;
    }

    /**
     * 返回参数错误详情。
     *
     * @return 参数错误详情；微信未返回时为 {@code null}
     */
    public @Nullable WechatPayApiErrorDetail detail() {
        return detail;
    }

    /**
     * 返回微信支付 Request-ID。
     *
     * @return Request-ID；响应未携带时为 {@code null}
     */
    public @Nullable String requestId() {
        return requestId;
    }

    /**
     * 构造不包含响应业务值的安全异常文本。
     *
     * @param statusCode HTTP 响应状态码
     * @param verified 错误正文是否通过响应签名验证
     * @param code 微信支付错误码；缺失时在文本中替换为 {@code UNKNOWN}
     * @param requestId 可选的微信支付请求标识
     * @return 可安全记录的异常消息
     */
    private static String format(
            int statusCode,
            boolean verified,
            String code,
            @Nullable String requestId
    ) {
        StringBuilder message = new StringBuilder("微信支付 API 调用失败: status=")
                .append(statusCode)
                .append(", code=")
                .append(code == null || code.isBlank() ? "UNKNOWN" : code);
        if (!verified) {
            message.append(", verified=false");
        }
        if (requestId != null && !requestId.isBlank()) {
            message.append(", requestId=").append(requestId);
        }
        return message.toString();
    }
}
