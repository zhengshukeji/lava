/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.HttpException;
import com.zhengshuyun.lava.http.HttpHeaders;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.http.HttpRequest;
import com.zhengshuyun.lava.http.HttpResponse;
import com.zhengshuyun.lava.http.HttpUrlBuilder;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityFailure;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayTransportException;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 支付宝公钥模式 OpenAPI V3 REST 传输层。
 */
public final class AlipayTransport implements AutoCloseable {
    /** OpenAPI V3 公钥模式固定使用的 RSA2 鉴权方案。 */
    private static final String V3_AUTH_SCHEME = "ALIPAY-SHA256withRSA";
    /** 承载应用 ID、时间戳、随机串和请求签名的鉴权头。 */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    /** 最长 32 字符的请求幂等追踪标识头。 */
    private static final String HEADER_REQUEST_ID = "alipay-request-id";
    /** 支付宝 V3 响应 RSA2 签名头。 */
    private static final String HEADER_SIGNATURE = "alipay-signature";
    /** 支付宝 V3 响应签名时间戳头。 */
    private static final String HEADER_TIMESTAMP = "alipay-timestamp";
    /** 支付宝 V3 响应签名随机串头。 */
    private static final String HEADER_NONCE = "alipay-nonce";
    /** 公共协议定义的支付宝链路标识头。 */
    private static final String HEADER_TRACE_ID = "alipay-trace-id";
    /** 部分接口元数据使用的兼容链路标识头。 */
    private static final String HEADER_TRACE_ID_COMPATIBLE = "alipay-traceid";
    /** 当前传输层绑定的应用 ID。 */
    private final String appId;
    /** 用于请求签名的应用私钥。 */
    private final PrivateKey appPrivateKey;
    /** 共享且已禁用隐式重试和重定向的 HTTP 客户端。 */
    private final HttpClient httpClient;
    /** 是否由本传输层负责关闭 HTTP 客户端；调用方借入的客户端为 false。 */
    private final boolean ownsHttpClient;
    /** 根客户端关闭状态，保证关闭幂等并拒绝后续调用。 */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** 不包含接口路径、查询参数和片段的 OpenAPI 基础地址。 */
    private final URI baseUrl;
    /** 生成协议时间戳所使用的时钟。 */
    private final Clock clock;
    /** 请求与响应共用的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;
    /** 执行 V3 响应头验签并解析业务 JSON 的解析器。 */
    private final AlipayResponseParser responseParser;

    /**
     * 创建内部传输层；参数已由根客户端构建器校验。
     *
     * @param appId           应用 ID
     * @param appPrivateKey   应用私钥
     * @param alipayPublicKey 支付宝公钥
     * @param httpClient      HTTP 客户端
     * @param ownsHttpClient  是否由本传输层负责关闭 HTTP 客户端
     * @param baseUrl         OpenAPI 基础地址
     * @param clock           协议时钟
     * @param jsonCodec       JSON 编解码器
     */
    public AlipayTransport(
            String appId,
            PrivateKey appPrivateKey,
            PublicKey alipayPublicKey,
            HttpClient httpClient,
            boolean ownsHttpClient,
            URI baseUrl,
            Clock clock,
            JsonCodec jsonCodec
    ) {
        this.appId = appId;
        this.appPrivateKey = appPrivateKey;
        this.httpClient = httpClient;
        this.ownsHttpClient = ownsHttpClient;
        this.baseUrl = baseUrl;
        this.clock = clock;
        this.jsonCodec = jsonCodec;
        responseParser = new AlipayResponseParser(alipayPublicKey, jsonCodec);
    }

    /**
     * 调用支付宝 OpenAPI V3，并在反序列化前验证原始响应签名。
     *
     * @param path         以 {@code /v3/} 开头的 REST 路径
     * @param method       HTTP 方法
     * @param requestBody  可选 JSON 请求对象
     * @param queryParams  查询参数，迭代顺序就是发送与签名顺序
     * @param responseType 业务响应类型
     * @param <T>          业务响应类型
     * @return 已验签并解析的业务响应
     */
    public <T> T execute(
            String path,
            HttpMethod method,
            @Nullable Object requestBody,
            Map<String, String> queryParams,
            Class<T> responseType
    ) {
        ensureOpen();
        // 1. 先构造最终发送 URL 和原始 JSON，签名必须覆盖完全相同的编码结果和字节内容。
        URI endpoint = endpoint(path, queryParams);
        String body = requestBody == null ? "" : encode(requestBody);
        String requestUri = endpoint.getRawPath()
                + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());

        // 2. 每次请求生成独立时间戳、nonce 和请求 ID，再按 V3 固定换行格式计算 Authorization。
        String nonce = requestId();
        String authString = "app_id=" + appId
                + ",nonce=" + nonce
                + ",timestamp=" + clock.millis();
        String signatureSource = authString + "\n"
                + method.name() + "\n"
                + requestUri + "\n"
                + body + "\n";
        String authorization = V3_AUTH_SCHEME + " " + authString
                + ",sign=" + AlipayCryptoUtils.sign(signatureSource, appPrivateKey);

        HttpRequest.Builder builder = HttpRequest.builder(endpoint, method)
                .header("Accept", "application/json")
                .header(HEADER_AUTHORIZATION, authorization)
                .header(HEADER_REQUEST_ID, requestId());
        if (requestBody != null) {
            builder.jsonBody(body);
        }

        // 3. 发送失败包装为领域异常并保留原始 cause；响应正文必须先验签，之后才允许进入错误或业务解析。
        HttpResponse response;
        try {
            response = httpClient.send(builder.build());
        } catch (HttpException exception) {
            throw new AlipayTransportException(exception);
        }
        HttpHeaders responseHeaders = response.headers();
        String responseSignature = signatureHeader(responseHeaders, HEADER_SIGNATURE);
        String responseTimestamp = signatureHeader(responseHeaders, HEADER_TIMESTAMP);
        String responseNonce = signatureHeader(responseHeaders, HEADER_NONCE);
        if (response.statusCode() != 200) {
            boolean hasSignatureMetadata = responseParser.hasSignatureMetadata(
                    responseSignature,
                    responseTimestamp,
                    responseNonce
            );
            String traceId = response.header(HEADER_TRACE_ID);
            if (traceId == null) {
                // 部分接口元数据省略了 trace 与 id 之间的连字符，兼容读取但始终优先公共协议名称。
                traceId = response.header(HEADER_TRACE_ID_COMPATIBLE);
            }
            try {
                throw responseParser.parseError(
                        response.statusCode(),
                        hasSignatureMetadata,
                        response.bodyBytes(),
                        responseSignature,
                        responseTimestamp,
                        responseNonce,
                        traceId
                );
            } catch (AlipayProtocolException exception) {
                // 官方 V3 SDK允许错误响应不带签名；无法安全结构化时仅保留 HTTP 状态。
                throw new AlipayTransportException(response.statusCode(), exception);
            }
        }
        return responseParser.parseSuccess(
                response.bodyBytes(),
                responseType,
                responseSignature,
                responseTimestamp,
                responseNonce
        );
    }

    /**
     * 使用最终 query 编码结果构造 V3 端点。
     *
     * @param path        V3 路径
     * @param queryParams 查询参数
     * @return 最终发送端点
     */
    private URI endpoint(String path, Map<String, String> queryParams) {
        if (!path.startsWith("/v3/")) {
            throw new IllegalArgumentException("V3 path must start with /v3/");
        }
        HttpUrlBuilder builder = HttpUrlBuilder.from(baseUrl).encodedPath(path);
        queryParams.forEach(builder::queryParam);
        return builder.build();
    }

    /**
     * 将请求模型编码为会直接参与签名和发送的 JSON 原文。
     *
     * @param value 请求模型
     * @return JSON 原文
     */
    private String encode(Object value) {
        try {
            return jsonCodec.write(value);
        } catch (JsonException exception) {
            throw new AlipayProtocolException("无法编码支付宝请求 JSON", exception);
        }
    }

    /**
     * 生成符合支付宝当前 32 字符限制的唯一请求标识或 nonce。
     *
     * @return 去除连字符的 UUID
     */
    private static String requestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 读取最多出现一次的 V3 签名元数据头。
     *
     * @param headers 原始响应头
     * @param name    响应头名称
     * @return 唯一响应头值；没有时为 {@code null}
     * @throws AlipaySecurityException 同名响应头出现多次
     */
    private static @Nullable String signatureHeader(HttpHeaders headers, String name) {
        List<String> values = headers.values(name);
        if (values.size() > 1) {
            throw new AlipaySecurityException(
                    AlipaySecurityFailure.DUPLICATE_SIGNATURE_HEADER
            );
        }
        return values.isEmpty() ? null : values.getFirst();
    }

    /**
     * 确保根客户端仍处于可用状态。
     *
     * @throws IllegalStateException 根客户端已经关闭
     */
    public void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("AlipayClient is closed");
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
}
