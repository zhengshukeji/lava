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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 诊断输出（日志、异常消息、toString）使用的凭证脱敏规则。
 *
 * <p>名称按两条规则判定敏感：规整为小写字母数字后以敏感词结尾（{@code X-Api-Key}、{@code accessToken}），
 * 或拆分出的某个逻辑词本身是敏感词（{@code secret_key}、{@code token_url}、{@code password_hint}）。
 * 只按整词匹配，因此 {@code tokenizer}、{@code secretary} 不会被误伤。URL 去掉用户信息与 fragment，
 * 并替换敏感查询参数的值；查询参数同时按 {@code &} 与 {@code ;} 分隔，服务端可能接受任一种。
 */
final class HttpRedactionUtils {

    static final String REDACTED = "[REDACTED]";

    /**
     * 查询串中的占位值，已按查询组件编码，可直接写回 encodedQuery。
     */
    private static final String ENCODED_REDACTED = "%5BREDACTED%5D";

    /**
     * 规整后名称以这些词结尾时视为敏感；"cookie" 同时覆盖 Set-Cookie。
     */
    private static final String[] SENSITIVE_SUFFIXES = {
            "authorization", "cookie", "token", "secret", "password", "passwd",
            "apikey", "credential", "credentials", "signature"};

    /**
     * 名称中任一逻辑词命中即视为敏感，覆盖敏感词不在末尾的命名。
     */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "authorization", "cookie", "token", "secret", "password", "passwd",
            "credential", "credentials", "signature");

    /**
     * 值为单个 URL 的标准头；另外名称以 url/uri 结尾的自定义头（如 X-Callback-Url）也按 URL 处理。
     */
    private static final Set<String> URL_VALUED_HEADERS = Set.of(
            "location", "content-location", "referer", "referrer", "destination");

    /**
     * Refresh 头中 URL 部分的起点，例如 {@code 5; url='https://...'}。
     */
    private static final Pattern REFRESH_URL = Pattern.compile("(?i)(?:^|[;\\s])url\\s*=\\s*");

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
        for (String suffix : SENSITIVE_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        if (normalized.equals("sig")) {
            return true;
        }

        List<String> words = logicalWords(name);
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            if (SENSITIVE_WORDS.contains(word)) {
                return true;
            }
            // api_key_id 这类 "api" + "key" 不在末尾的命名
            if (word.equals("api") && index + 1 < words.size() && words.get(index + 1).equals("key")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回请求头值的诊断形式：敏感头整体替换，携带 URL 的头按 URL 规则脱敏，其余原样返回。
     */
    static String redactHeaderValue(String name, String value) {
        if (isSensitiveName(name)) {
            return REDACTED;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("link")) {
            return redactLink(value);
        }
        if (normalized.equals("refresh")) {
            Matcher matcher = REFRESH_URL.matcher(value);
            return matcher.find()
                    ? value.substring(0, matcher.end()) + redactReference(value.substring(matcher.end()).strip())
                    : value;
        }
        if (URL_VALUED_HEADERS.contains(normalized) || endsWithUrlWord(name)) {
            return redactReference(value);
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
        // 不用 OkHttp 的 queryParameterNames：它只按 & 切分，a=1;token=x 会被当成一个参数漏掉
        String query = url.encodedQuery();
        if (query != null) {
            builder.encodedQuery(redactQuery(query));
        }
        return builder.build().toString();
    }

    /**
     * 脱敏头值中的 URL 引用，可以是绝对、协议相对或相对地址，也可能带引号。
     */
    private static String redactReference(String reference) {
        int length = reference.length();
        if (length >= 2 && (reference.charAt(0) == '"' || reference.charAt(0) == '\'')
                && reference.charAt(length - 1) == reference.charAt(0)) {
            char quote = reference.charAt(0);
            return quote + redactReference(reference.substring(1, length - 1)) + quote;
        }
        HttpUrl absolute = HttpUrl.parse(reference);
        if (absolute != null) {
            return redact(absolute);
        }
        if (reference.startsWith("//")) {
            HttpUrl networkPath = HttpUrl.parse("http:" + reference);
            if (networkPath != null) {
                return redact(networkPath).substring("http:".length());
            }
        }
        if (reference.contains("://")) {
            // 非 HTTP(S) 的绝对 URL 可能在 authority 中带凭证，无法可靠拆分时整体隐藏
            return "[invalid URL]";
        }
        // 相对地址：丢弃 fragment，只替换查询参数，路径原样保留
        int fragment = reference.indexOf('#');
        String withoutFragment = fragment < 0 ? reference : reference.substring(0, fragment);
        int query = withoutFragment.indexOf('?');
        if (query < 0) {
            return withoutFragment;
        }
        return withoutFragment.substring(0, query + 1) + redactQuery(withoutFragment.substring(query + 1));
    }

    /**
     * 脱敏 Link 头中每个尖括号包裹的 URI，rel 等参数原样保留。
     */
    private static String redactLink(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int from = 0;
        while (from < value.length()) {
            int open = value.indexOf('<', from);
            int close = open < 0 ? -1 : value.indexOf('>', open + 1);
            if (close < 0) {
                // 剩余部分不是完整的 <URI>，仍按 URL 引用处理，避免残缺值原样泄露查询参数
                result.append(redactReference(value.substring(from)));
                break;
            }
            result.append(value, from, open + 1)
                    .append(redactReference(value.substring(open + 1, close)))
                    .append('>');
            from = close + 1;
        }
        return result.toString();
    }

    /**
     * 替换已编码查询串中敏感参数的值，保留原有分隔符与非敏感参数。
     */
    private static String redactQuery(String query) {
        StringBuilder result = new StringBuilder(query.length());
        int start = 0;
        while (start <= query.length()) {
            int end = start;
            while (end < query.length() && query.charAt(end) != '&' && query.charAt(end) != ';') {
                end++;
            }
            int equals = query.indexOf('=', start);
            if (equals < 0 || equals > end) {
                equals = end;
            }
            String rawName = query.substring(start, equals);
            if (isSensitiveName(decode(rawName))) {
                result.append(rawName).append('=').append(ENCODED_REDACTED);
            } else {
                result.append(query, start, end);
            }
            if (end == query.length()) {
                break;
            }
            result.append(query.charAt(end));
            start = end + 1;
        }
        return result.toString();
    }

    /**
     * 解码参数名，使 {@code access%54oken} 这类编码形式也能命中规则；非法编码按原文判断。
     */
    private static String decode(String name) {
        try {
            return URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return name;
        }
    }

    private static boolean endsWithUrlWord(String name) {
        List<String> words = logicalWords(name);
        if (words.isEmpty()) {
            return false;
        }
        String last = words.getLast();
        return last.equals("url") || last.equals("uri");
    }

    /**
     * 按非字母数字字符和驼峰边界拆分名称，统一为小写；连续大写视为缩写（{@code XMLToken} 拆为 xml、token）。
     */
    private static List<String> logicalWords(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!Character.isLetterOrDigit(character)) {
                flush(words, current);
                continue;
            }
            if (Character.isUpperCase(character) && !current.isEmpty()) {
                char previous = name.charAt(index - 1);
                boolean afterLowercase = Character.isLowerCase(previous) || Character.isDigit(previous);
                boolean endsAcronym = Character.isUpperCase(previous)
                        && index + 1 < name.length() && Character.isLowerCase(name.charAt(index + 1));
                if (afterLowercase || endsAcronym) {
                    flush(words, current);
                }
            }
            current.append(Character.toLowerCase(character));
        }
        flush(words, current);
        return words;
    }

    private static void flush(List<String> words, StringBuilder current) {
        if (!current.isEmpty()) {
            words.add(current.toString());
            current.setLength(0);
        }
    }
}
