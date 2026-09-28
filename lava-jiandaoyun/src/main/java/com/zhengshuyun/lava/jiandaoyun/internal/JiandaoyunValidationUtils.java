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

package com.zhengshuyun.lava.jiandaoyun.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.net.URI;
import java.util.Locale;

/**
 * 简道云参数校验。
 *
 * <p>只校验必填项和本 SDK 无法正确发出请求的情况；ID 格式等业务规则由简道云服务端校验。</p>
 */
public final class JiandaoyunValidationUtils {

    private JiandaoyunValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验应用 ID 非空白。
     *
     * @param value 应用 ID
     * @return 原值
     */
    public static String requireAppId(String value) {
        return ValidationUtils.requireNotBlank(value, "appId must not be blank");
    }

    /**
     * 校验表单 ID 非空白。
     *
     * @param value 表单 ID
     * @return 原值
     */
    public static String requireEntryId(String value) {
        return ValidationUtils.requireNotBlank(value, "entryId must not be blank");
    }

    /**
     * 校验通用必填文本非空白。
     *
     * @param value 参数值
     * @param name 参数名，用于异常消息
     * @return 原值
     */
    public static String requireNotBlank(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank");
    }

    /**
     * 校验文件上传地址：必须是 HTTPS 绝对地址，环回主机允许 HTTP 以支持本地协议测试。
     *
     * @param value 上传地址
     * @return 原值
     */
    public static URI requireUploadUrl(URI value) {
        ValidationUtils.requireNonNull(value, "upload url must not be null");
        ValidationUtils.requireTrue(isSecureOrLoopback(value),
                "upload url must be an absolute HTTPS URL (HTTP is only allowed for loopback hosts)");
        return value;
    }

    /**
     * 判断地址是否为 HTTPS 绝对地址，或指向环回主机的 HTTP 地址。
     *
     * @param value 待判断地址
     * @return 满足传输安全要求时为 {@code true}
     */
    private static boolean isSecureOrLoopback(URI value) {
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        String host = value.getHost();
        boolean loopback = host != null && ("localhost".equalsIgnoreCase(host) || host.startsWith("127.")
                || "[::1]".equals(host) || "::1".equals(host));
        return value.isAbsolute() && host != null
                && (scheme.equals("https") || scheme.equals("http") && loopback);
    }

    /**
     * 校验 API 根地址并统一补齐末尾斜杠。
     *
     * <p>API Key 以 Bearer 头发送，因此只允许 HTTPS，环回地址可用 HTTP 做本地协议测试；根地址不得带
     * 路径、查询参数、片段或用户信息，路径由传输层拼接。</p>
     *
     * @param value API 根地址
     * @return 以斜杠结尾的根地址
     */
    public static URI requireApiBaseUrl(URI value) {
        ValidationUtils.requireNonNull(value, "apiBaseUrl must not be null");
        ValidationUtils.requireTrue(isSecureOrLoopback(value),
                "apiBaseUrl must be an absolute HTTPS URL (HTTP is only allowed for loopback hosts)");
        ValidationUtils.requireTrue(value.getRawQuery() == null && value.getRawFragment() == null
                        && value.getUserInfo() == null,
                "apiBaseUrl must not contain user information, query, or fragment");
        ValidationUtils.requireTrue(value.getRawPath() == null || value.getRawPath().isEmpty()
                        || "/".equals(value.getRawPath()),
                "apiBaseUrl must not contain a path");
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + '/');
    }
}
