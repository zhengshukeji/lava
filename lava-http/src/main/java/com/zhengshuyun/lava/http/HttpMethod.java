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

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * HTTP 请求方法
 * <p>
 * 表示标准及扩展 HTTP 请求方法.
 *
 * @author Toint
 * @since 2026/1/8
 */
public final class HttpMethod {

    /**
     * HTTP GET 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3">HTTP 1.1, section 9.3</a>
     */
    public static final HttpMethod GET = new HttpMethod("GET");

    /**
     * HTTP HEAD 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4">HTTP 1.1, section 9.4</a>
     */
    public static final HttpMethod HEAD = new HttpMethod("HEAD");

    /**
     * HTTP POST 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5">HTTP 1.1, section 9.5</a>
     */
    public static final HttpMethod POST = new HttpMethod("POST");

    /**
     * HTTP PUT 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6">HTTP 1.1, section 9.6</a>
     */
    public static final HttpMethod PUT = new HttpMethod("PUT");

    /**
     * HTTP PATCH 方法
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc5789#section-2">RFC 5789</a>
     */
    public static final HttpMethod PATCH = new HttpMethod("PATCH");

    /**
     * HTTP DELETE 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7">HTTP 1.1, section 9.7</a>
     */
    public static final HttpMethod DELETE = new HttpMethod("DELETE");

    /**
     * HTTP OPTIONS 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2">HTTP 1.1, section 9.2</a>
     */
    public static final HttpMethod OPTIONS = new HttpMethod("OPTIONS");

    /**
     * HTTP TRACE 方法
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8">HTTP 1.1, section 9.8</a>
     */
    public static final HttpMethod TRACE = new HttpMethod("TRACE");

    private static final HttpMethod[] values = new HttpMethod[]{GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE};

    private final String name;

    private HttpMethod(String name) {
        ValidationUtils.requireNonNull(name, "method name must not be null");
        this.name = name;
    }

    /**
     * 返回包含所有标准 HTTP 方法的数组.
     * <p>
     * 具体来说, 此方法返回包含 {@link #GET}、{@link #HEAD}、
     * {@link #POST}、{@link #PUT}、{@link #PATCH}、{@link #DELETE}、
     * {@link #OPTIONS} 和 {@link #TRACE} 的数组.
     */
    public static HttpMethod[] values() {
        HttpMethod[] copy = new HttpMethod[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        return copy;
    }

    /**
     * 根据给定的字符串值返回对应的 {@code HttpMethod} 对象.
     * <p>
     * 如果是标准方法 (GET、POST 等) , 返回预定义的实例；
     * 否则创建一个新的实例.
     *
     * @param method 方法值 (字符串)
     * @return 对应的 {@code HttpMethod}
     * @throws IllegalArgumentException 如果 method 为 blank
     */
    public static HttpMethod valueOf(String method) {
        ValidationUtils.requireNotBlank(method, "method must not be blank");
        // 必须固定 Locale.ROOT: 土耳其语等区域下 "i".toUpperCase() 会得到 "İ" (U+0130),
        // 导致 options/patch 等含 i 的方法名解析成非法方法
        String methodUpperCase = method.toUpperCase(Locale.ROOT);
        return switch (methodUpperCase) {
            case "GET" -> GET;
            case "HEAD" -> HEAD;
            case "POST" -> POST;
            case "PUT" -> PUT;
            case "PATCH" -> PATCH;
            case "DELETE" -> DELETE;
            case "OPTIONS" -> OPTIONS;
            case "TRACE" -> TRACE;
            default -> new HttpMethod(methodUpperCase);
        };
    }

    /**
     * 返回此方法的名称, 例如 "GET"、"POST".
     */
    public String name() {
        return name;
    }

    /**
     * 判断该方法是否允许携带请求体
     * <p>
     * 语义与 OkHttp 一致: 除 GET 和 HEAD 之外的方法都允许携带请求体.
     * <p>
     * 这里刻意自己实现而不复用 {@code okhttp3.internal.http.HttpMethod}:
     * 那是 OkHttp 的 internal 包, 不属于公开 API 契约, 随时可能改签名或改包路径,
     * 基础库不应该把稳定性押在上游内部实现上.
     */
    public boolean permitsRequestBody() {
        return !("GET".equals(name) || "HEAD".equals(name));
    }

    /**
     * 判断该方法是否必须携带请求体
     * <p>
     * 语义与 OkHttp 一致, 覆盖 POST、PUT、PATCH、QUERY 以及 WebDAV 的 PROPPATCH、REPORT.
     *
     * @see #permitsRequestBody()
     */
    public boolean requiresRequestBody() {
        return switch (name) {
            case "POST", "PUT", "PATCH", "QUERY", "PROPPATCH", "REPORT" -> true;
            default -> false;
        };
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        HttpMethod that = (HttpMethod) object;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
