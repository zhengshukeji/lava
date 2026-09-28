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


package com.zhengshuyun.lava.http;

import okhttp3.HttpUrl;

import java.util.Locale;
import java.util.Set;

/**
 * 诊断输出（日志、异常消息、toString）使用的凭证脱敏规则。
 *
 * <p>名称先规整为小写字母数字（{@code X-Api-Key} 变为 {@code xapikey}），以敏感词结尾即视为敏感，
 * 因此 {@code accessToken}、{@code client_secret} 会被脱敏，而 {@code tokenizer}、{@code secretary}
 * 不会。URL 去掉用户信息与 fragment，并替换敏感查询参数的值。
 */
final class HttpRedactionUtils {

    static final String REDACTED = "[REDACTED]";

    /**
     * 规整后名称以这些词结尾时视为敏感。
     */
    private static final String[] SENSITIVE_SUFFIXES = {
            "authorization", "cookie", "token", "secret", "password", "passwd",
            "apikey", "credential", "credentials", "signature"};

    /**
     * 值为 URL 的常见响应/请求头，其查询参数可能携带凭证。
     */
    private static final Set<String> URL_VALUED_HEADERS = Set.of("location", "content-location", "referer");

    private HttpRedactionUtils() {
    }

    /**
     * 判断请求头或查询参数名称是否敏感。
     */
    static boolean isSensitiveName(String name) {
        StringBuilder compact = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                compact.append(Character.toLowerCase(character));
            }
        }
        String normalized = compact.toString();
        // "cookie" 同时覆盖 Set-Cookie
        for (String suffix : SENSITIVE_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return normalized.equals("sig");
    }

    /**
     * 返回请求头值的诊断形式：敏感头整体替换，URL 值的头按 URL 规则脱敏，其余原样返回。
     */
    static String redactHeaderValue(String name, String value) {
        if (isSensitiveName(name)) {
            return REDACTED;
        }
        if (URL_VALUED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            // 相对地址借助占位主机解析，只替换查询参数
            HttpUrl absolute = HttpUrl.parse(value);
            if (absolute != null) {
                return redact(absolute);
            }
            HttpUrl relative = HttpUrl.parse("http://relative.invalid/").resolve(value);
            if (relative != null && relative.encodedQuery() != null) {
                String redacted = redact(relative);
                return redacted.substring("http://relative.invalid".length());
            }
        }
        return value;
    }

    /**
     * 返回 URL 的诊断形式；无法解析为 HTTP(S) URL 时返回占位文本，避免原样输出。
     */
    static String redactUrl(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed == null ? "[invalid URL]" : redact(parsed);
    }

    private static String redact(HttpUrl url) {
        HttpUrl.Builder builder = url.newBuilder().fragment(null);
        if (!url.username().isEmpty()) {
            builder.username(REDACTED);
        }
        if (!url.password().isEmpty()) {
            builder.password(REDACTED);
        }
        for (String name : url.queryParameterNames()) {
            if (isSensitiveName(name)) {
                builder.setQueryParameter(name, REDACTED);
            }
        }
        return builder.build().toString();
    }
}
