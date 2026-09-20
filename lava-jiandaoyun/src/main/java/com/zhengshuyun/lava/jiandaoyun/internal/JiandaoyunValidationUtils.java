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
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;

/**
 * 简道云领域参数校验工具。
 *
 * <p>集中维护简道云 API 对标识和根地址的约束。校验失败一律抛出
 * {@link IllegalArgumentException}，与模块异常体系（仅表示已进入协议处理的失败）区分。</p>
 */
public final class JiandaoyunValidationUtils {
    /** 简道云应用和表单等标识的最大字符数。 */
    private static final int MAX_ID_LENGTH = 64;

    /** 禁止实例化简道云校验工具。 */
    private JiandaoyunValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验简道云应用 ID。
     *
     * @param value 应用 ID
     * @return 原值
     */
    public static String requireAppId(String value) {
        return requireId(value, "appId");
    }

    /**
     * 校验简道云表单 ID。
     *
     * @param value 表单 ID
     * @return 原值
     */
    public static String requireEntryId(String value) {
        return requireId(value, "entryId");
    }

    /**
     * 校验简道云非空标识。
     *
     * <p>简道云标识目前为 24 位十六进制文本；此处仅约束非空和长度上限，不做字符集限制，
     * 避免简道云未来引入新格式时误拒合法值。</p>
     *
     * @param value 标识值
     * @param name 用于报错的参数名称
     * @return 原值
     */
    public static String requireId(String value, String name) {
        ValidationUtils.requireNonNull(value, name + " must not be null");
        ValidationUtils.requireNotBlank(value, name + " must not be blank");
        int characters = value.codePointCount(0, value.length());
        ValidationUtils.requireTrue(characters <= MAX_ID_LENGTH,
                name + " must not exceed " + MAX_ID_LENGTH + " characters");
        return value;
    }

    /**
     * 校验并规范化简道云 API 根地址。
     *
     * <p>生产环境只接受简道云官方域名，自定义地址仅允许环回主机用于本地协议测试。
     * 传输层自行拼接 API 路径，根地址不得预置路径、查询参数或片段。</p>
     *
     * @param value API 根地址
     * @return 以斜杠结尾的根地址
     */
    public static URI requireApiBaseUrl(URI value) {
        // 1. 根地址必须唯一标识 HTTP 服务，不能混入用户信息或请求级参数。
        ValidationUtils.requireNonNull(value, "apiBaseUrl must not be null");
        ValidationUtils.requireTrue(value.isAbsolute(), "apiBaseUrl must be absolute");
        ValidationUtils.requireTrue(
                value.getHost() != null && !value.getHost().isBlank(),
                "apiBaseUrl must contain a host"
        );

        // 2. 生产请求只发送到官方域名；自定义地址仅用于环回协议测试。
        String scheme = value.getScheme();
        String host = value.getHost().toLowerCase(Locale.ROOT);
        boolean official = "https".equalsIgnoreCase(scheme)
                && "api.jiandaoyun.com".equals(host)
                && (value.getPort() == -1 || value.getPort() == 443);
        boolean localTest = isLoopbackHost(host)
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        ValidationUtils.requireTrue(
                official || localTest,
                "apiBaseUrl must use the official Jiandaoyun HTTPS host or a loopback test host"
        );

        // 3. 传输层自行拼接 API 路径，根地址不得预置路径、查询参数或片段。
        ValidationUtils.requireTrue(
                value.getRawQuery() == null
                        && value.getRawFragment() == null
                        && value.getUserInfo() == null,
                "apiBaseUrl must not contain user information, query, or fragment"
        );
        ValidationUtils.requireTrue(
                value.getRawPath() == null
                        || value.getRawPath().isEmpty()
                        || "/".equals(value.getRawPath()),
                "apiBaseUrl must not contain a path"
        );

        // 4. 统一保留末尾斜杠，保证后续 URI 拼接不依赖调用方输入形式。
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + '/');
    }

    /**
     * 判断主机名是否为允许在测试网关中使用的本地环回地址。
     *
     * @param host 待判断的 URI 主机文本
     * @return 属于环回地址时返回 {@code true}
     */
    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || host.startsWith("127.")
                || "[::1]".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }
}
