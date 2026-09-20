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

import java.net.URI;

/**
 * 简道云开放 API 的统一鉴权、发送和错误解析传输层。
 *
 * <p>该类型仅由根客户端及各功能入口共享使用，集中保证每个请求携带 Bearer API Key、
 * 成功响应按业务模型解析、失败响应转换为结构化领域异常，并将底层 HTTP 失败转换为简道云
 * 领域异常。它不拥有 HTTP 客户端的生命周期。</p>
 */
public final class JiandaoyunTransport {
    /** 客户端在 User-Agent 中声明的产品名。 */
    private static final String USER_AGENT = "lava-jiandaoyun";

    /** 简道云开放 API 的 Bearer API Key；关闭客户端时清除引用。 */
    private String apiKey;
    /** 仅用于发送请求的 HTTP 客户端；关闭责任由共享运行时或调用方承担。 */
    private final HttpClient httpClient;
    /** 已校验的简道云 API 根地址，用于构造业务接口端点。 */
    private final URI apiBaseUrl;
    /** 业务请求编码及响应解码使用的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;

    /**
     * 创建内部传输层。调用方负责保证配置已完成校验。
     *
     * @param apiKey 简道云 API Key
     * @param httpClient HTTP 客户端
     * @param apiBaseUrl API 根地址
     * @param jsonCodec JSON 编解码器
     */
    public JiandaoyunTransport(
            String apiKey,
            HttpClient httpClient,
            URI apiBaseUrl,
            JsonCodec jsonCodec
    ) {
        this.apiKey = ValidationUtils.requireNonNull(apiKey, "apiKey must not be null");
        this.httpClient = ValidationUtils.requireNonNull(
                httpClient,
                "httpClient must not be null"
        );
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
     * 发送携带 Bearer 鉴权的 JSON POST 请求，成功响应解析为业务模型。
     *
     * <p>简道云开放 API 的查询类接口统一使用 POST 传输 JSON 请求体。成功（HTTP 2xx）响应
     * 直接反序列化为业务模型；非 2xx 响应按 {@code {"code", "msg"}} 结构转换为
     * {@link JiandaoyunApiException}，无法解析时视为协议异常。</p>
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     * @param responseType 响应模型
     * @param <T> 响应类型
     * @return 响应模型
     */
    public <T> T post(URI uri, Object requestBody, Class<T> responseType) {
        // 1. 编码一次请求正文，后续发送使用同一份数据，避免二次序列化。
        byte[] body = encode(requestBody);
        HttpResponse response = execute(uri, body);
        byte[] responseBody = response.getBodyAsBytes();

        // 2. 成功响应解析为业务模型；空正文或结构不符属于协议异常。
        if (response.isSuccessful()) {
            if (responseBody.length == 0) {
                throw new JiandaoyunProtocolException("简道云成功响应缺少正文");
            }
            try {
                return jsonCodec.read(responseBody, responseType);
            } catch (JsonException | IllegalArgumentException exception) {
                throw new JiandaoyunProtocolException("简道云成功响应不是预期的 JSON 结构");
            }
        }

        // 3. 失败响应按简道云错误结构解析为可编程判断的领域异常。
        throw apiException(response.statusCode(), responseBody);
    }

    /**
     * 执行单次携带 Bearer 鉴权的 HTTP 请求，并将底层 HTTP 失败转换为脱敏领域异常。
     *
     * @param uri 最终请求地址
     * @param body 请求正文字节
     * @return 尚未解析的 HTTP 响应
     */
    private HttpResponse execute(URI uri, byte[] body) {
        HttpRequest request = HttpRequest.builder(uri, HttpMethod.POST)
                .header(HttpHeaderNames.ACCEPT, HttpMediaTypes.APPLICATION_JSON)
                .bearerToken(apiKey)
                .userAgent(USER_AGENT)
                .body(body, HttpMediaTypes.APPLICATION_JSON)
                .build();
        try {
            return httpClient.send(request);
        } catch (HttpException exception) {
            throw new JiandaoyunTransportException(
                    exception.getKind(),
                    exception.getMethod(),
                    exception.getUrl(),
                    exception.getTransportCauseType()
            );
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
            throw new JiandaoyunProtocolException("简道云请求正文编码失败");
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
            throw new JiandaoyunProtocolException("简道云错误响应缺少 code/msg 结构");
        }
    }

    /**
     * 清除 API Key 引用。运行时关闭时调用，尽力降低密钥在内存中的滞留时间。
     */
    public void clearSecret() {
        apiKey = null;
    }

    /**
     * 简道云错误响应正文结构。
     *
     * @param code 简道云错误码
     * @param msg 错误描述
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorPayload(int code, @Nullable String msg) {
    }
}
