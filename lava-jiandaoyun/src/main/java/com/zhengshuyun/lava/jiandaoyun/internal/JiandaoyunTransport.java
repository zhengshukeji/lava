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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.HttpException;
import com.zhengshuyun.lava.http.HttpHeaderNames;
import com.zhengshuyun.lava.http.HttpMediaTypes;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.http.HttpRequest;
import com.zhengshuyun.lava.http.HttpResponse;
import com.zhengshuyun.lava.http.HttpUrlBuilder;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunApiException;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunTransportException;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简道云开放 API 的统一鉴权、发送和错误解析传输层。
 *
 * <p>该类型仅由根客户端及各功能入口共享使用，集中保证每个请求携带 Bearer API Key、
 * 成功响应按业务模型解析、失败响应转换为结构化领域异常，并将底层 HTTP 失败转换为简道云
 * 领域异常。传输层同时持有根客户端的关闭状态；关闭时只关闭自己创建的 HTTP 客户端。</p>
 */
public final class JiandaoyunTransport implements AutoCloseable {
    /** 客户端在 User-Agent 中声明的产品名。 */
    private static final String USER_AGENT = "lava-jiandaoyun";
    /** 流程类接口在 2xx 响应中表示业务失败的 status 取值。 */
    private static final String FAILURE_STATUS = "failure";

    /** 简道云开放 API 的 Bearer API Key。 */
    private final String apiKey;
    /** 发送请求的 HTTP 客户端。 */
    private final HttpClient httpClient;
    /** 是否由本传输层负责关闭 HTTP 客户端；调用方借入的客户端为 false。 */
    private final boolean ownsHttpClient;
    /** 根客户端关闭状态，保证关闭幂等并拒绝后续调用。 */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** 已校验的简道云 API 根地址，用于构造业务接口端点。 */
    private final URI apiBaseUrl;
    /** 业务请求编码及响应解码使用的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;

    /**
     * 创建内部传输层。调用方负责保证配置已完成校验。
     *
     * @param apiKey 简道云 API Key
     * @param httpClient HTTP 客户端
     * @param ownsHttpClient 是否由本传输层负责关闭 HTTP 客户端
     * @param apiBaseUrl API 根地址
     * @param jsonCodec JSON 编解码器
     */
    public JiandaoyunTransport(
            String apiKey,
            HttpClient httpClient,
            boolean ownsHttpClient,
            URI apiBaseUrl,
            JsonCodec jsonCodec
    ) {
        this.apiKey = ValidationUtils.requireNonNull(apiKey, "apiKey must not be null");
        this.httpClient = ValidationUtils.requireNonNull(
                httpClient,
                "httpClient must not be null"
        );
        this.ownsHttpClient = ownsHttpClient;
        this.apiBaseUrl = JiandaoyunValidationUtils.requireApiBaseUrl(apiBaseUrl);
        this.jsonCodec = ValidationUtils.requireNonNull(
                jsonCodec,
                "jsonCodec must not be null"
        );
    }

    /**
     * 从配置的 API 根地址创建固定路径端点。
     *
     * @param path API 绝对路径
     * @return 完整 URI
     */
    public URI endpoint(String path) {
        return HttpUrlBuilder.from(apiBaseUrl).path(path).build();
    }

    /**
     * 从配置的 API 根地址创建带路径参数的端点，每个参数作为独立路径段编码。
     *
     * @param path API 绝对路径前缀
     * @param segments 依次追加的路径参数
     * @param suffix 路径参数之后的固定路径段
     * @return 完整 URI
     */
    public URI endpoint(String path, List<String> segments, String suffix) {
        HttpUrlBuilder builder = HttpUrlBuilder.from(apiBaseUrl).path(path);
        segments.forEach(builder::appendPathSegment);
        return builder.appendPathSegment(suffix).build();
    }

    /**
     * 发送携带 Bearer 鉴权的 JSON POST 请求，成功响应解析为业务模型。
     *
     * <p>简道云开放 API 统一使用 POST 传输 JSON 请求体。非 2xx 响应按 {@code {"code", "msg"}} 结构转换为
     * {@link JiandaoyunApiException}；流程类接口在 2xx 下也可能返回 {@code {"status": "failure"}}，
     * 同样按业务失败处理。</p>
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     * @param responseType 响应模型
     * @param <T> 响应类型
     * @return 响应模型
     */
    public <T> T post(URI uri, Object requestBody, Class<T> responseType) {
        JsonNode tree = send(uri, requestBody);
        if (tree == null) {
            throw new JiandaoyunProtocolException("简道云成功响应缺少正文");
        }
        try {
            return jsonCodec.convert(tree, responseType);
        } catch (JsonException | IllegalArgumentException exception) {
            throw new JiandaoyunProtocolException("简道云成功响应不是预期的 JSON 结构", exception);
        }
    }

    /**
     * 发送不关心响应内容的写操作请求，例如删除和成员增减。
     *
     * <p>部分接口成功时只返回 HTTP 200 空正文，因此允许正文为空；有正文时仍会识别
     * {@code {"status": "failure"}} 业务失败。</p>
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     */
    public void postVoid(URI uri, Object requestBody) {
        send(uri, requestBody);
    }

    /**
     * 使用 {@code get_upload_token} 返回的凭证把本地文件上传到对象存储。
     *
     * <p>上传地址属于第三方对象存储（七牛），请求不携带简道云 API Key；按对象存储约定，
     * {@code token} 字段在前、{@code file} 字段在最后。</p>
     *
     * @param url 上传地址
     * @param token 上传凭证
     * @param file 待上传文件
     * @param contentType 文件媒体类型
     * @return 对象存储返回的文件 key，用于附件和图片字段取值
     */
    public String upload(URI url, String token, Path file, String contentType) {
        ensureOpen();
        HttpRequest request = HttpRequest.builder(url, HttpMethod.POST)
                .header(HttpHeaderNames.ACCEPT, HttpMediaTypes.APPLICATION_JSON)
                .userAgent(USER_AGENT)
                .multipartBody(HttpRequest.MultipartBuilder.builder()
                        .addFormField("token", token)
                        .addFile("file", file, contentType))
                .build();
        HttpResponse response = send(request);
        byte[] responseBody = response.bodyBytes();
        if (!response.isSuccessful()) {
            // 七牛错误正文为 {"error": "..."}，错误码即 HTTP 状态码（如 401 凭证无效、614 文件已存在）
            throw new JiandaoyunApiException(response.statusCode(), response.statusCode(),
                    readUploadError(responseBody));
        }
        try {
            UploadPayload payload = jsonCodec.read(responseBody, UploadPayload.class);
            return ValidationUtils.requireNotBlank(payload.key(), "key");
        } catch (JsonException | IllegalArgumentException exception) {
            throw new JiandaoyunProtocolException("文件上传成功响应缺少 key", exception);
        }
    }

    /**
     * 发送 JSON 请求并返回成功响应的树模型，统一处理 HTTP 失败和 2xx 业务失败。
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     * @return 响应树；成功响应正文为空时为 {@code null}
     */
    private @Nullable JsonNode send(URI uri, Object requestBody) {
        ensureOpen();
        // 1. 编码一次请求正文，后续发送使用同一份数据，避免二次序列化。
        byte[] body = encode(requestBody);
        HttpResponse response = send(HttpRequest.builder(uri, HttpMethod.POST)
                .header(HttpHeaderNames.ACCEPT, HttpMediaTypes.APPLICATION_JSON)
                .bearerToken(apiKey)
                .userAgent(USER_AGENT)
                .body(body, HttpMediaTypes.APPLICATION_JSON)
                .build());
        byte[] responseBody = response.bodyBytes();

        // 2. 失败响应按简道云错误结构解析为可编程判断的领域异常。
        if (!response.isSuccessful()) {
            throw apiException(response.statusCode(), responseBody);
        }
        if (responseBody.length == 0) {
            return null;
        }

        // 3. 成功状态码下仍需识别 {"status": "failure", "code", "message"} 形式的业务失败。
        JsonNode tree;
        try {
            tree = jsonCodec.readTree(new String(responseBody, StandardCharsets.UTF_8));
        } catch (JsonException exception) {
            throw new JiandaoyunProtocolException("简道云成功响应不是合法 JSON", exception);
        }
        JsonNode status = tree.get("status");
        if (status != null && status.isString() && FAILURE_STATUS.equals(status.stringValue())) {
            throw apiException(response.statusCode(), responseBody);
        }
        return tree;
    }

    /**
     * 执行单次 HTTP 请求，并将底层 HTTP 失败转换为脱敏领域异常。
     *
     * @param request 已构建的请求
     * @return 尚未解析的 HTTP 响应
     */
    private HttpResponse send(HttpRequest request) {
        try {
            return httpClient.send(request);
        } catch (HttpException exception) {
            throw new JiandaoyunTransportException(exception);
        }
    }

    /**
     * 编码请求模型为 JSON 字节。
     *
     * @param requestBody 请求模型
     * @return JSON 正文字节
     */
    private byte[] encode(Object requestBody) {
        ValidationUtils.requireNonNull(requestBody, "requestBody must not be null");
        try {
            return jsonCodec.writeBytes(requestBody);
        } catch (JsonException exception) {
            throw new JiandaoyunProtocolException("简道云请求正文编码失败", exception);
        }
    }

    /**
     * 解析简道云错误响应正文并转换为结构化异常。
     *
     * @param statusCode HTTP 响应状态码
     * @param responseBody 错误响应正文
     * @return 结构化 API 异常
     */
    private JiandaoyunApiException apiException(int statusCode, byte[] responseBody) {
        try {
            ApiErrorPayload payload = jsonCodec.read(responseBody, ApiErrorPayload.class);
            return new JiandaoyunApiException(statusCode, payload.code(), payload.msg());
        } catch (JsonException | IllegalArgumentException exception) {
            throw new JiandaoyunProtocolException("简道云错误响应缺少 code/msg 结构", exception);
        }
    }

    /**
     * 尽力读取对象存储错误描述；正文不是预期结构时返回 {@code null}。
     *
     * @param responseBody 错误响应正文
     * @return 错误描述
     */
    private @Nullable String readUploadError(byte[] responseBody) {
        try {
            return jsonCodec.read(responseBody, UploadErrorPayload.class).error();
        } catch (JsonException | IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 确保根客户端仍处于可用状态。
     *
     * @throws IllegalStateException 根客户端已经关闭
     */
    public void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("JiandaoyunClient is closed");
        }
    }

    /**
     * 关闭传输层；仅关闭本传输层拥有的 HTTP 客户端，可重复调用。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownsHttpClient) {
            httpClient.close();
        }
    }

    /**
     * 简道云错误响应正文结构。
     *
     * @param code 简道云错误码
     * @param msg 错误描述
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorPayload(int code, @JsonAlias("message") @Nullable String msg) {
    }

    /**
     * 对象存储上传成功响应正文结构。
     *
     * @param key 文件 key
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UploadPayload(String key) {
    }

    /**
     * 对象存储上传失败响应正文结构。
     *
     * @param error 错误描述
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UploadErrorPayload(@Nullable String error) {
    }
}
