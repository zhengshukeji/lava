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
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已完整缓冲的 HTTP 响应；HTTP 错误状态码会正常表示。
 */
public final class HttpResponse {
    /**
     * 服务端返回的 HTTP 状态码。
     */
    private final int code;
    /**
     * 服务端返回的 HTTP 状态文本。
     */
    private final String message;
    /**
     * 响应头快照。
     */
    private final HttpHeaders headers;
    /**
     * 已完整缓冲的响应正文。
     */
    private final byte[] body;
    /**
     * 从 Content-Type 推断出的正文字符集。
     */
    private final Charset charset;
    /**
     * 已脱敏的调用元数据。
     */
    private final HttpCallMetadata metadata;
    /**
     * 协商后的 HTTP 协议名称。
     */
    private final String protocol;
    /**
     * 用于解码 JSON 正文的客户端编解码器。
     */
    private final JsonCodec jsonCodec;

    HttpResponse(int code, String message, HttpHeaders headers, byte[] body, Charset charset,
                 HttpCallMetadata metadata, String protocol) {
        this(code, message, headers, body, charset, metadata, protocol, JsonCodec.defaultCodec());
    }

    HttpResponse(int code, String message, HttpHeaders headers, byte[] body, Charset charset,
                 HttpCallMetadata metadata, String protocol, JsonCodec jsonCodec) {
        this.code = code;
        this.message = message;
        this.headers = headers;
        this.body = body.clone();
        this.charset = charset;
        this.metadata = metadata;
        this.protocol = protocol;
        this.jsonCodec = ValidationUtils.requireNonNull(jsonCodec, "jsonCodec");
    }

    /**
     * 返回 HTTP 状态码。
     *
     * @return 状态码
     */
    public int statusCode() {
        return code;
    }

    /**
     * 返回 HTTP 状态文本；HTTP/2 响应通常为空字符串。
     *
     * @return 状态文本
     */
    public String statusMessage() {
        return message;
    }

    /**
     * 判断响应状态码是否为 2xx。
     *
     * @return 2xx 时返回 true
     */
    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    /**
     * 判断响应状态码是否为 3xx。
     *
     * @return 3xx 时返回 true
     */
    public boolean isRedirect() {
        return code >= 300 && code < 400;
    }

    /**
     * 返回响应头快照。
     *
     * @return 响应头
     */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * 返回指定名称的全部响应头值。
     *
     * @param name 响应头名称，不区分大小写
     * @return 响应头值列表
     */
    public List<String> headers(String name) {
        return headers.values(name);
    }

    /**
     * 返回指定名称的最后一个响应头值。
     *
     * @param name 响应头名称，不区分大小写
     * @return 响应头值；不存在时为 null
     */
    public @Nullable String header(String name) {
        return headers.get(name);
    }

    /**
     * 返回指定名称的最后一个响应头值，不存在时返回默认值。
     *
     * @param name         响应头名称，不区分大小写
     * @param defaultValue 默认值
     * @return 响应头值或默认值
     */
    public String header(String name, String defaultValue) {
        String value = header(name);
        return value == null ? defaultValue : value;
    }

    /**
     * 返回 {@code Content-Type} 响应头。
     *
     * @return 媒体类型；不存在时为 null
     */
    public @Nullable String contentType() {
        return header(HttpHeaderNames.CONTENT_TYPE);
    }

    /**
     * 返回已缓冲正文的字节数。
     *
     * @return 正文字节数
     */
    public long contentLength() {
        return body.length;
    }

    /**
     * 返回 {@code Location} 响应头，常用于未自动跟随的重定向。
     *
     * @return 跳转地址；不存在时为 null
     */
    public @Nullable String location() {
        return header(HttpHeaderNames.LOCATION);
    }

    /**
     * 解析全部 {@code Set-Cookie} 响应头中的名称与值，同名 Cookie 以最后一个为准。
     *
     * @return 不可变的 Cookie 名称到值的映射
     */
    public Map<String, String> cookies() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String header : headers.values(HttpHeaderNames.SET_COOKIE)) {
            int semicolon = header.indexOf(';');
            String pair = semicolon >= 0 ? header.substring(0, semicolon) : header;
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String name = pair.substring(0, equals).strip();
                String value = pair.substring(equals + 1).strip();
                // RFC 6265 允许 Cookie 值带双引号，返回时去掉
                if (value.length() >= 2 && value.charAt(0) == '"'
                        && value.charAt(value.length() - 1) == '"') {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(name, value);
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 返回指定名称的 Cookie 值。
     *
     * @param name Cookie 名称
     * @return Cookie 值；不存在时为 null
     */
    public @Nullable String cookie(String name) {
        return cookies().get(ValidationUtils.requireNotBlank(name, "cookie name must not be blank"));
    }

    /**
     * 返回正文字节的副本。
     *
     * @return 正文字节
     */
    public byte[] bodyBytes() {
        return body.clone();
    }

    /**
     * 按响应字符集解码正文。
     *
     * @return 正文文本
     */
    public String bodyString() {
        return new String(body, charset);
    }

    /**
     * 按指定字符集解码正文，用于服务端声明的字符集不可信的场景。
     *
     * @param charset 解码字符集
     * @return 正文文本
     */
    public String bodyString(Charset charset) {
        return new String(body, ValidationUtils.requireNonNull(charset, "charset must not be null"));
    }

    /**
     * 返回从 {@code Content-Type} 推断的字符集，缺省为 UTF-8。
     *
     * @return 响应字符集
     */
    public Charset charset() {
        return charset;
    }

    /**
     * 返回已脱敏的调用元数据。
     *
     * @return 调用元数据
     */
    public HttpCallMetadata metadata() {
        return metadata;
    }

    /**
     * 返回协商的 HTTP 协议，例如 {@code http/1.1}。
     *
     * @return 协议名称
     */
    public String protocol() {
        return protocol;
    }

    /**
     * 显式要求 2xx；失败时保留有界响应上下文。
     */
    public HttpResponse requireSuccess() {
        if (!isSuccessful()) {
            throw new HttpStatusException(this);
        }
        return this;
    }

    /**
     * 使用客户端配置的 JSON 编解码器读取响应。
     */
    public <T> T bodyAs(Class<T> type) {
        return jsonCodec.read(body,
                ValidationUtils.requireNonNull(type, "type must not be null"));
    }

    /**
     * 使用客户端配置的 JSON 编解码器读取带泛型信息的响应。
     */
    public <T> T bodyAs(TypeReference<T> type) {
        return jsonCodec.read(body,
                ValidationUtils.requireNonNull(type, "type must not be null"));
    }

    @Override
    public String toString() {
        return metadata.toString();
    }

    static Charset responseCharset(okhttp3.Response response) {
        okhttp3.MediaType contentType = response.body().contentType();
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        Charset result = contentType.charset(StandardCharsets.UTF_8);
        return result == null ? StandardCharsets.UTF_8 : result;
    }
}
