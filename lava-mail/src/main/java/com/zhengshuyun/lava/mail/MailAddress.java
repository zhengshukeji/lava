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
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.jspecify.annotations.Nullable;

/**
 * 经过严格语法校验的互联网邮箱地址及可选显示名。
 *
 * @param address     只包含一个 mailbox 的邮箱地址，不接受形如 {@code Name <user@example.com>} 的组合文本
 * @param displayName 可选显示名
 */
public record MailAddress(String address, @Nullable String displayName) {
    /**
     * 校验并规范化邮箱地址。
     *
     * @param address     邮箱地址
     * @param displayName 可选显示名
     */
    public MailAddress {
        address = MailValidationUtils.requireNonBlank(address, "address");
        if (containsControl(address)) {
            throw new IllegalArgumentException("address must not contain control characters");
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(address, true);
            if (parsed.length != 1 || !address.equals(parsed[0].getAddress())) {
                throw new IllegalArgumentException("address must contain exactly one mailbox");
            }
            parsed[0].validate();
        } catch (AddressException exception) {
            throw new IllegalArgumentException("address is not a valid Internet mailbox", exception);
        }
        if (displayName != null) {
            displayName = ValidationUtils.requireNotBlank(
                    displayName, "displayName must not be blank").strip();
            if (containsControl(displayName)) {
                throw new IllegalArgumentException("displayName must be non-blank and contain no control characters");
            }
        }
    }

    /**
     * 创建不带显示名的邮箱地址。
     *
     * @param address 邮箱地址
     */
    public MailAddress(String address) {
        this(address, null);
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
