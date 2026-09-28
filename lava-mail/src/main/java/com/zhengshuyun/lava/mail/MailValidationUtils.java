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

/**
 * 邮件模块内部的参数校验。控制字符检查用于阻止 CRLF 等字符进入邮件头或 IMAP 命令。
 */
final class MailValidationUtils {

    private MailValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 要求文本非空白，返回去除首尾空白后的值。
     */
    static String requireNonBlank(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank").strip();
    }

    /**
     * 要求文本非空白，原样返回；用于首尾空白有意义的凭证类值。
     */
    static String requireNonBlankPreserved(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank");
    }

    /**
     * 要求文本非空白且不含控制字符，返回去除首尾空白后的值。
     */
    static String requireNonBlankWithoutControls(String value, String name) {
        String normalized = requireNonBlank(value, name);
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        return normalized;
    }
}
