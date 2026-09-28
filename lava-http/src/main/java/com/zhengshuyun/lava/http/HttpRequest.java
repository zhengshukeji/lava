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
import com.zhengshuyun.lava.json.JsonCodec;
import okhttp3.*;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 不可变的 HTTP 请求；常规公开 API 仅使用 Lava 和 JDK 类型。
 */
public final class HttpRequest {
    /**
     * 未显式指定时使用的正文文本字符集。
     */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 对外展示的 URL；传输时会重新基于原始相对路径解析。
     */
    private final String url;
    /**
     * 构建时的原始 URL，用于保留相对路径和 query 参数语义。
     */
    private final String rawUrl;
    /**
     * 以插入顺序保存的 query 参数变更。
     */
    private final List<QueryParam> queryParams;
    /**
     * HTTP 请求方法。
     */
    private final HttpMethod method;
    /**
     * 由文本和表单构建器使用的字符集。
     */
    private final Charset charset;
    /**
     * 请求级 HTTP 头。
     */
    private final HttpHeaders headers;
    /**
     * 请求体；只在发送时适配为 OkHttp 请求体。
     */
    private final @Nullable HttpBody body;

    private HttpRequest(Builder builder) {
        method = builder.method;
        charset = builder.charset;
        headers = builder.headers.build();
        body = builder.body;
        rawUrl = builder.url;
        queryParams = List.copyOf(builder.queryParams);
        url = resolveUrl(builder.url, builder.queryParams);
    }

    /**
     * 返回构建时可展示的 URL。
     *
     * @return 已包含请求级 query 参数的 URL
     */
    public String url() {
        return url;
    }

    /**
     * 返回 HTTP 请求方法。
     *
     * @return 请求方法
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * 返回请求级请求头，不包含客户端默认头。
     *
     * @return 请求头
     */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * 使用客户端的同步发送入口，便于在业务代码中保持统一调用方向。
     *
     * @param client 目标客户端
     * @return 同步响应
     */
    public HttpResponse send(HttpClient client) {
        requireClient(client);
        return client.send(this);
    }

    Request toOkHttpRequest() {
        return toOkHttpRequest(null, HttpHeaders.of());
    }

    Request toOkHttpRequest(@Nullable URI baseUrl, HttpHeaders defaults) {
        RequestBody requestBody = body == null ? null : HttpBodyUtils.toOkHttp(body);
        if (requestBody != null && !method.permitsRequestBody()) {
            throw new IllegalStateException("HTTP " + method + " must not have a request body");
        }
        if (requestBody == null && method.requiresRequestBody()) {
            requestBody = RequestBody.create(new byte[0]);
        }
        HttpHeaders effectiveHeaders = mergeHeaders(defaults, headers);
        return new Request.Builder()
                .url(resolvedUrl(baseUrl))
                .headers(effectiveHeaders.toOkHttp())
                .method(method.name(), requestBody)
                .build();
    }

    String resolvedUrl(@Nullable URI baseUrl) {
        String resolved = resolveUrl(rawUrl, baseUrl);
        if (queryParams.isEmpty()) {
            return resolved;
        }
        HttpUrl parsed = HttpUrl.parse(resolved);
        if (parsed == null) {
            throw new IllegalArgumentException("url must be an HTTP or HTTPS URL");
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        applyQueryParams(builder, queryParams);
        return builder.build().toString();
    }

    HttpHeaders effectiveHeaders(HttpHeaders defaults) {
        return mergeHeaders(defaults, headers);
    }

    /**
     * 返回带有一个覆盖 header 的新请求。
     *
     * @param name  请求头名称
     * @param value 请求头值
     * @return 带覆盖请求头的新请求
     */
    public HttpRequest withHeader(String name, String value) {
        Builder builder = builder(rawUrl, method, charset);
        copyQueryParams(builder);
        builder.headers(headers);
        if (body != null) {
            builder.body(body);
        }
        builder.header(name, value);
        return builder.build();
    }

    /**
     * 返回带有新请求体的新请求；原请求保持不变。
     *
     * @param value 新请求体
     * @return 带新请求体的新请求
     */
    public HttpRequest withBody(HttpBody value) {
        ValidationUtils.requireNonNull(value, "body must not be null");
        Builder builder = builder(rawUrl, method, charset);
        copyQueryParams(builder);
        builder.headers(headers);
        builder.body(value);
        return builder.build();
    }

    /**
     * 创建指定 URL 和方法的请求构建器。
     *
     * @param url    目标 URL；可为相对路径
     * @param method HTTP 方法
     * @return 新的请求构建器
     */
    public static Builder builder(String url, HttpMethod method) {
        return new Builder(url, method, DEFAULT_CHARSET);
    }

    /**
     * 创建指定 URL、方法和字符集的请求构建器。
     *
     * @param url     目标 URL；可为相对路径
     * @param method  HTTP 方法
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder builder(String url, HttpMethod method, Charset charset) {
        return new Builder(url, method, charset);
    }

    /**
     * 创建指定 URI 和方法的请求构建器。
     *
     * @param url    目标 URI；可为相对路径
     * @param method HTTP 方法
     * @return 新的请求构建器
     */
    public static Builder builder(URI url, HttpMethod method) {
        return builder(ValidationUtils.requireNonNull(url, "url must not be null").toString(), method);
    }

    /**
     * 创建 GET 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder get(String url) {
        return builder(url, HttpMethod.GET);
    }

    /**
     * 创建 GET 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder get(URI url) {
        return builder(url, HttpMethod.GET);
    }

    /**
     * 创建 GET 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder get(String url, Charset charset) {
        return builder(url, HttpMethod.GET, charset);
    }

    /**
     * 创建 POST 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder post(String url) {
        return builder(url, HttpMethod.POST);
    }

    /**
     * 创建 POST 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder post(URI url) {
        return builder(url, HttpMethod.POST);
    }

    /**
     * 创建 POST 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder post(String url, Charset charset) {
        return builder(url, HttpMethod.POST, charset);
    }

    /**
     * 创建 PUT 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder put(String url) {
        return builder(url, HttpMethod.PUT);
    }

    /**
     * 创建 PUT 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder put(URI url) {
        return builder(url, HttpMethod.PUT);
    }

    /**
     * 创建 PUT 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder put(String url, Charset charset) {
        return builder(url, HttpMethod.PUT, charset);
    }

    /**
     * 创建 DELETE 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder delete(String url) {
        return builder(url, HttpMethod.DELETE);
    }

    /**
     * 创建 DELETE 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder delete(URI url) {
        return builder(url, HttpMethod.DELETE);
    }

    /**
     * 创建 DELETE 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder delete(String url, Charset charset) {
        return builder(url, HttpMethod.DELETE, charset);
    }

    /**
     * 创建 PATCH 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder patch(String url) {
        return builder(url, HttpMethod.PATCH);
    }

    /**
     * 创建 PATCH 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder patch(URI url) {
        return builder(url, HttpMethod.PATCH);
    }

    /**
     * 创建 PATCH 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder patch(String url, Charset charset) {
        return builder(url, HttpMethod.PATCH, charset);
    }

    /**
     * 创建 HEAD 请求构建器。
     *
     * @param url 目标 URL
     * @return 新的请求构建器
     */
    public static Builder head(String url) {
        return builder(url, HttpMethod.HEAD);
    }

    /**
     * 创建 HEAD 请求构建器。
     *
     * @param url 目标 URI
     * @return 新的请求构建器
     */
    public static Builder head(URI url) {
        return builder(url, HttpMethod.HEAD);
    }

    /**
     * 创建 HEAD 请求构建器并指定字符集。
     *
     * @param url     目标 URL
     * @param charset 文本与表单正文使用的字符集
     * @return 新的请求构建器
     */
    public static Builder head(String url, Charset charset) {
        return builder(url, HttpMethod.HEAD, charset);
    }

    @Override
    public String toString() {
        return "HttpRequest[method=" + method + ", url=" + HttpRedactionUtils.redactUrl(url)
                + ", headers=" + headers + ']';
    }

    private static void requireClient(@Nullable HttpClient client) {
        ValidationUtils.requireNonNull(client, "client must not be null");
    }

    private static String resolveUrl(String rawUrl, List<QueryParam> queryParams) {
        HttpUrl parsed = HttpUrl.parse(rawUrl);
        if (parsed == null) {
            // 相对路径允许在 HttpClient 的 baseUrl 上解析；此处只校验 URI 语法。
            try {
                URI value = new URI(rawUrl);
                if (value.isAbsolute() && !("http".equalsIgnoreCase(value.getScheme())
                        || "https".equalsIgnoreCase(value.getScheme()))) {
                    throw new IllegalArgumentException("url must use HTTP or HTTPS");
                }
                if (value.isAbsolute()) {
                    throw new IllegalArgumentException("url must be a valid HTTP or HTTPS URL");
                }
                if (!queryParams.isEmpty()) {
                    return renderRelativeUrl(rawUrl, queryParams);
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("url must be a valid URI", exception);
            }
            return rawUrl;
        }
        requireHttpScheme(parsed);
        if (queryParams.isEmpty()) {
            return rawUrl;
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        applyQueryParams(builder, queryParams);
        return builder.build().toString();
    }

    /**
     * 在不改变相对路径语义的前提下渲染请求级 query 参数。
     */
    private static String renderRelativeUrl(String rawUrl, List<QueryParam> queryParams) {
        int fragmentStart = rawUrl.indexOf('#');
        String fragment = fragmentStart < 0 ? "" : rawUrl.substring(fragmentStart);
        String withoutFragment = fragmentStart < 0 ? rawUrl : rawUrl.substring(0, fragmentStart);
        int queryStart = withoutFragment.indexOf('?');
        String path = queryStart < 0 ? withoutFragment : withoutFragment.substring(0, queryStart);
        String existingQuery = queryStart < 0 ? "" : withoutFragment.substring(queryStart + 1);
        HttpUrl synthetic = HttpUrl.parse("http://relative.invalid/?" + existingQuery);
        if (synthetic == null) {
            throw new IllegalArgumentException("url must be a valid URI");
        }
        HttpUrl.Builder builder = synthetic.newBuilder();
        applyQueryParams(builder, queryParams);
        String encodedQuery = builder.build().encodedQuery();
        return path + (encodedQuery == null ? "" : "?" + encodedQuery) + fragment;
    }

    private static void applyQueryParams(HttpUrl.Builder builder, List<QueryParam> queryParams) {
        for (QueryParam param : queryParams) {
            if (param.replace) {
                builder.removeAllQueryParameters(param.name);
            }
            builder.addQueryParameter(param.name, param.value);
        }
    }

    private void copyQueryParams(Builder builder) {
        for (QueryParam param : queryParams) {
            if (param.replace) {
                builder.queryParam(param.name, param.value);
            } else {
                builder.addQueryParam(param.name, param.value);
            }
        }
    }

    private static String resolveUrl(String rawUrl, @Nullable URI baseUrl) {
        URI value;
        try {
            value = new URI(rawUrl);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("url must be a valid URI", exception);
        }
        if (!value.isAbsolute()) {
            if (baseUrl == null) {
                throw new IllegalArgumentException("relative URL requires a client baseUrl");
            }
            value = baseUrl.resolve(value);
        }
        HttpUrl parsed = HttpUrl.parse(value.toString());
        if (parsed == null) {
            throw new IllegalArgumentException("url must be an HTTP or HTTPS URL");
        }
        requireHttpScheme(parsed);
        return parsed.toString();
    }

    private static void requireHttpScheme(HttpUrl url) {
        if (!url.isHttps() && !"http".equalsIgnoreCase(url.scheme())) {
            throw new IllegalArgumentException("url must use HTTP or HTTPS");
        }
    }

    private static HttpHeaders mergeHeaders(HttpHeaders defaults, HttpHeaders request) {
        HttpHeaders.Builder builder = HttpHeaders.builder();
        builder.addAll(defaults);
        for (String name : request.names()) {
            // 请求级同名头完整覆盖默认值，同时保留该名称的多值语义。
            builder.remove(name);
            for (String value : request.values(name)) {
                builder.add(name, value);
            }
        }
        return builder.build();
    }

    private record QueryParam(String name, @Nullable String value, boolean replace) {
    }

    /**
     * {@link HttpRequest} 的构建器
     */
    public static final class Builder {
        private final String url;
        private final HttpMethod method;
        private final Charset charset;
        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private final List<QueryParam> queryParams = new ArrayList<>();
        private @Nullable HttpBody body;

        private Builder(String url, HttpMethod method, Charset charset) {
            url = ValidationUtils.requireNotBlank(url, "url must not be blank");
            ValidationUtils.requireNonNull(method, "method must not be null");
            ValidationUtils.requireNonNull(charset, "charset must not be null");
            this.url = url;
            this.method = method;
            this.charset = charset;
        }

        /**
         * 设置请求头，覆盖该名称的全部已有值。
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /**
         * 追加请求头，保留该名称的已有值。
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder addHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        /**
         * 整体替换请求头。
         *
         * @param headers 新的请求头集合
         * @return 当前构建器
         */
        public Builder headers(HttpHeaders headers) {
            ValidationUtils.requireNonNull(headers, "headers must not be null");
            for (String name : List.copyOf(this.headers.build().names())) {
                this.headers.remove(name);
            }
            this.headers.addAll(headers);
            return this;
        }

        /**
         * 追加 query 参数，保留同名已有参数。
         *
         * @param name  参数名
         * @param value 参数值；可为 null
         * @return 当前构建器
         */
        public Builder addQueryParam(String name, @Nullable String value) {
            requireQueryName(name);
            queryParams.add(new QueryParam(name, value, false));
            return this;
        }

        /**
         * 设置 query 参数，先移除该名称的全部已有参数。
         *
         * @param name  参数名
         * @param value 参数值；可为 null
         * @return 当前构建器
         */
        public Builder queryParam(String name, @Nullable String value) {
            requireQueryName(name);
            queryParams.add(new QueryParam(name, value, true));
            return this;
        }

        /**
         * 批量追加 query 参数。
         *
         * @param params 参数名到参数值的映射
         * @return 当前构建器
         */
        public Builder addQueryParams(Map<String, String> params) {
            ValidationUtils.requireNonNull(params, "params must not be null");
            params.forEach(this::addQueryParam);
            return this;
        }

        /**
         * 设置 User-Agent 请求头。
         *
         * @param userAgent User-Agent 值
         * @return 当前构建器
         */
        public Builder userAgent(String userAgent) {
            return header(HttpHeaderNames.USER_AGENT, userAgent);
        }

        /**
         * 设置内置浏览器 User-Agent。
         *
         * @return 当前构建器
         */
        public Builder userAgentBrowser() {
            return userAgent(HttpUserAgents.DEFAULT);
        }

        /**
         * 设置 Authorization 请求头。
         *
         * @param authorization 完整的 Authorization 值
         * @return 当前构建器
         */
        public Builder authorization(String authorization) {
            return header(HttpHeaderNames.AUTHORIZATION, authorization);
        }

        /**
         * 设置 Bearer 令牌，即 Authorization: Bearer 请求头。
         *
         * @param token 令牌值
         * @return 当前构建器
         */
        public Builder bearerToken(String token) {
            return authorization("Bearer " + ValidationUtils.requireNonNull(token, "token must not be null"));
        }

        /**
         * 设置 Basic 认证头，凭据按构建字符集编码。
         *
         * @param username 用户名
         * @param password 密码
         * @return 当前构建器
         */
        public Builder basicAuth(String username, String password) {
            ValidationUtils.requireNonNull(username, "username must not be null");
            ValidationUtils.requireNonNull(password, "password must not be null");
            byte[] credentials = (username + ':' + password).getBytes(charset);
            return authorization("Basic " + Base64.getEncoder().encodeToString(credentials));
        }

        /**
         * 设置 Cookie 请求头。
         *
         * @param cookie Cookie 值
         * @return 当前构建器
         */
        public Builder cookie(String cookie) {
            return header(HttpHeaderNames.COOKIE, cookie);
        }

        /**
         * 设置 JSON 文本请求体。
         *
         * @param json JSON 文本
         * @return 当前构建器
         */
        public Builder jsonBody(String json) {
            return body(json, HttpMediaTypes.APPLICATION_JSON);
        }

        /**
         * 设置 XML 文本请求体。
         *
         * @param xml XML 文本
         * @return 当前构建器
         */
        public Builder xmlBody(String xml) {
            return body(xml, HttpMediaTypes.APPLICATION_XML);
        }

        /**
         * 设置纯文本请求体。
         *
         * @param text 文本内容
         * @return 当前构建器
         */
        public Builder textBody(String text) {
            return body(text, HttpMediaTypes.TEXT_PLAIN);
        }

        /**
         * 设置指定媒体类型的文本请求体，按构建字符集编码。
         *
         * @param value       文本内容
         * @param contentType 媒体类型
         * @return 当前构建器
         */
        public Builder body(String value, String contentType) {
            ValidationUtils.requireNonNull(value, "body must not be null");
            return body(HttpBodyUtils.bytes(value.getBytes(charset), contentType));
        }

        /**
         * 设置指定媒体类型的字节请求体。
         *
         * @param value       字节内容
         * @param contentType 媒体类型
         * @return 当前构建器
         */
        public Builder body(byte[] value, String contentType) {
            return body(HttpBodyUtils.bytes(value, contentType));
        }

        /**
         * 设置不依赖 OkHttp 的请求体。
         *
         * @param value 请求体
         * @return 当前构建器
         */
        public Builder body(HttpBody value) {
            this.body = ValidationUtils.requireNonNull(value, "body must not be null");
            return this;
        }

        /**
         * 使用默认 JSON 编解码器编码任意对象。
         *
         * @param value 待编码对象
         * @return 当前构建器
         */
        public Builder jsonBody(Object value) {
            return body(HttpBodyUtils.json(value));
        }

        /**
         * 使用指定编解码器编码任意对象。
         *
         * @param value 待编码对象
         * @param codec JSON 编解码器
         * @return 当前构建器
         */
        public Builder jsonBody(Object value, JsonCodec codec) {
            return body(HttpBodyUtils.json(value, codec));
        }

        /**
         * 设置表单请求体，按构建字符集编码。
         *
         * @param params 表单字段名到值的映射
         * @return 当前构建器
         */
        public Builder formBody(Map<String, String> params) {
            ValidationUtils.requireNonNull(params, "params must not be null");
            FormBody.Builder form = new FormBody.Builder(charset);
            params.forEach(form::add);
            return body(HttpBodyUtils.fromOkHttp(form.build()));
        }

        /**
         * 设置 multipart 表单请求体。
         *
         * @param multipart multipart 构建器
         * @return 当前构建器
         */
        public Builder multipartBody(MultipartBuilder multipart) {
            ValidationUtils.requireNonNull(multipart, "multipart must not be null");
            return body(HttpBodyUtils.fromOkHttp(multipart.build()));
        }

        Builder okHttpBody(RequestBody body) {
            return body(HttpBodyUtils.fromOkHttp(body));
        }

        /**
         * 构建不可变请求。
         *
         * @return 新的 HTTP 请求
         */
        public HttpRequest build() {
            return new HttpRequest(this);
        }

        private static void requireQueryName(@Nullable String name) {
            ValidationUtils.requireNotBlank(name, "query parameter name must not be blank");
        }
    }

    /**
     * {@link HttpRequest} 的 multipart 表单构建器
     */
    public static final class MultipartBuilder {
        private final MultipartBody.Builder delegate = new MultipartBody.Builder().setType(MultipartBody.FORM);

        private MultipartBuilder() {
        }

        /**
         * 创建 multipart 构建器。
         *
         * @return 新的构建器
         */
        public static MultipartBuilder builder() {
            return new MultipartBuilder();
        }

        /**
         * 添加文本表单字段。
         *
         * @param name  字段名
         * @param value 字段值
         * @return 当前构建器
         */
        public MultipartBuilder addFormField(String name, String value) {
            delegate.addFormDataPart(name, value);
            return this;
        }

        /**
         * 添加文件部分，媒体类型为 application/octet-stream。
         *
         * @param name 字段名
         * @param path 文件路径
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, Path path) {
            return addFile(name, path, HttpMediaTypes.APPLICATION_OCTET_STREAM);
        }

        /**
         * 添加文件部分并指定媒体类型。
         *
         * @param name        字段名
         * @param path        文件路径
         * @param contentType 媒体类型
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, Path path, String contentType) {
            ValidationUtils.requireNonNull(path, "path must not be null");
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("path must be a regular file");
            }
            return addFile(name, path.toFile(), contentType);
        }

        /**
         * 添加文件部分，媒体类型为 application/octet-stream。
         *
         * @param name 字段名
         * @param file 文件
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, File file) {
            return addFile(name, file, HttpMediaTypes.APPLICATION_OCTET_STREAM);
        }

        /**
         * 添加文件部分并指定媒体类型。
         *
         * @param name        字段名
         * @param file        文件
         * @param contentType 媒体类型
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, File file, String contentType) {
            ValidationUtils.requireNonNull(file, "file must not be null");
            if (!file.isFile()) {
                throw new IllegalArgumentException("file must be a regular file");
            }
            delegate.addFormDataPart(name, file.getName(), RequestBody.create(file, requireMediaType(contentType)));
            return this;
        }

        /**
         * 添加字节数组文件部分，媒体类型为 application/octet-stream。
         *
         * @param name     字段名
         * @param filename 文件名；可为 null
         * @param data     文件内容
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, @Nullable String filename, byte[] data) {
            return addFile(name, filename, data, HttpMediaTypes.APPLICATION_OCTET_STREAM);
        }

        /**
         * 添加字节数组文件部分并指定媒体类型。
         *
         * @param name        字段名
         * @param filename    文件名；可为 null
         * @param data        文件内容
         * @param contentType 媒体类型
         * @return 当前构建器
         */
        public MultipartBuilder addFile(String name, @Nullable String filename,
                                        byte[] data, String contentType) {
            ValidationUtils.requireNonNull(data, "data must not be null");
            delegate.addFormDataPart(name, filename,
                    RequestBody.create(data.clone(), requireMediaType(contentType)));
            return this;
        }

        MultipartBody build() {
            return delegate.build();
        }
    }

    private static MediaType requireMediaType(@Nullable String contentType) {
        ValidationUtils.requireNonNull(contentType, "contentType must not be null");
        MediaType result = MediaType.parse(contentType);
        if (result == null) {
            throw new IllegalArgumentException("invalid content type");
        }
        return result;
    }
}
