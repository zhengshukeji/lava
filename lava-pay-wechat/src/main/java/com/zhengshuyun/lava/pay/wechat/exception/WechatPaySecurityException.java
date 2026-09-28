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

import java.io.Serial;

/**
 * 微信支付签名、时间戳、回调密文、响应一致性或账单摘要校验失败。
 */
public final class WechatPaySecurityException extends WechatPayException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 稳定的安全失败类别。 */
    private final WechatPaySecurityFailure failure;

    /**
     * 创建安全校验异常。
     *
     * @param failure 精确失败类别
     */
    public WechatPaySecurityException(WechatPaySecurityFailure failure) {
        super("微信支付安全校验失败: " + ValidationUtils.requireNonNull(failure, "failure"));
        this.failure = failure;
    }

    /**
     * 创建保留底层异常的安全校验异常。
     *
     * @param failure 精确失败类别
     * @param cause   底层异常
     */
    public WechatPaySecurityException(WechatPaySecurityFailure failure, Throwable cause) {
        super("微信支付安全校验失败: " + ValidationUtils.requireNonNull(failure, "failure"), cause);
        this.failure = failure;
    }

    /**
     * 返回精确失败类别。
     *
     * @return 安全失败类别
     */
    public WechatPaySecurityFailure failure() {
        return failure;
    }
}
