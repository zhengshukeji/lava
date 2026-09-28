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
 * 用户名和密码认证凭证；诊断文本始终隐藏密码。
 */
public final class PasswordCredential implements MailCredential {
    private final String username;
    private final String password;

    /**
     * 创建密码凭证。密码属于不透明值，不会去除首尾空白。
     *
     * @param username 登录用户名
     * @param password 登录密码
     */
    public PasswordCredential(String username, String password) {
        this.username = MailValidationUtils.requireNonBlankWithoutControls(username, "username");
        this.password = ValidationUtils.requireNonNull(password, "password");
    }

    @Override
    public String username() {
        return username;
    }

    /**
     * 返回真实密码，仅可用于认证，调用方不得记录。
     *
     * @return 原始密码
     */
    public String password() {
        return password;
    }

    @Override
    public String toString() {
        return "PasswordCredential[username=" + username + ", password=<redacted>]";
    }
}
