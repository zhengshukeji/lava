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

import java.io.Serial;

/**
 * 简道云协议适配失败的基类。
 *
 * <p>非法调用参数仍使用 {@link IllegalArgumentException} 表达；本类型只表示调用已经进入
 * 简道云协议处理后产生的 API、传输或协议失败。</p>
 */
public abstract class JiandaoyunException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建不保留底层异常对象的简道云异常。
     *
     * @param message 不包含密钥和原始报文的诊断消息
     */
    protected JiandaoyunException(String message) {
        super(ValidationUtils.requireNotBlank(message, "message must not be blank"));
    }
}
