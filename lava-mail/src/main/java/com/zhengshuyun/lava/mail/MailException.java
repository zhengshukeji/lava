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
package com.zhengshuyun.lava.mail;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.io.Serial;

/**
 * 邮件操作失败时抛出的结构化异常。
 *
 * <p>异常消息和保留的诊断信息都不会包含服务器原始响应或凭证。为了便于诊断，仅记录原始异常的类型名，
 * 不通过 {@link #getCause()} 保留可能泄露敏感信息的异常对象。
 */
public final class MailException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 稳定的失败类别。
     */
    private final MailFailureKind kind;

    /**
     * 创建不带底层异常信息的邮件异常。
     *
     * @param kind    失败类别
     * @param message 凭证安全的错误消息
     */
    public MailException(MailFailureKind kind, String message) {
        super(ValidationUtils.requireNonNull(message, "message"));
        this.kind = ValidationUtils.requireNonNull(kind, "kind");
    }

    /**
     * 创建保留底层异常的邮件异常。
     *
     * @param kind    失败类别
     * @param message 凭证安全的错误消息
     * @param cause   底层异常
     */
    public MailException(MailFailureKind kind, String message, Throwable cause) {
        super(ValidationUtils.requireNonNull(message, "message"),
                ValidationUtils.requireNonNull(cause, "cause"));
        this.kind = ValidationUtils.requireNonNull(kind, "kind");
    }

    /**
     * 返回稳定的失败类别。
     *
     * @return 失败类别
     */
    public MailFailureKind kind() {
        return kind;
    }
}
