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

import java.io.Serial;

/**
 * 已通过传输层但无法按微信支付 APIv3 协议解释的响应或通知。
 */
public final class WechatPayProtocolException extends WechatPayException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建不包含原始报文的协议异常。
     *
     * @param message 精确协议失败原因
     */
    public WechatPayProtocolException(String message) {
        super(message);
    }

    /**
     * 创建保留底层异常的协议异常。
     *
     * @param message 诊断消息
     * @param cause   底层异常
     */
    public WechatPayProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
