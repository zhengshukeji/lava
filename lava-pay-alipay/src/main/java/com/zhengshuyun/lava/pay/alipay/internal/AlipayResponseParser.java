/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayApiException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityFailure;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付宝 OpenAPI V3 原始响应验签与 JSON 解析器。
 *
 * <p>V3 响应不再使用 {@code xxx_response + sign} 包装结构。支付宝通过响应头提供时间戳、
 * 随机数和签名，验签原文必须包含未经重新编码的原始响应正文。</p>
 */
public final class AlipayResponseParser {
    /** 用于验证支付宝 V3 响应的支付宝公钥。 */
    private final PublicKey alipayPublicKey;
    /** 验签成功后用于反序列化业务数据的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;

    /**
     * 创建响应解析器。
     *
     * @param alipayPublicKey 支付宝公钥
     * @param jsonCodec       JSON 编解码器
     */
    public AlipayResponseParser(PublicKey alipayPublicKey, JsonCodec jsonCodec) {
        this.alipayPublicKey = AlipayKeyUtils.requirePublicKey(alipayPublicKey);
        this.jsonCodec = ValidationUtils.requireNonNull(
                jsonCodec,
                "jsonCodec must not be null"
        );
    }

    /**
     * 验签并解析成功响应。
     *
     * @param body         原始 UTF-8 响应正文
     * @param responseType 业务响应类型
     * @param signature    {@code alipay-signature} 响应头
     * @param timestamp    {@code alipay-timestamp} 响应头
     * @param nonce        {@code alipay-nonce} 响应头
     * @param <T>          业务响应类型
     * @return 已验签业务响应
     */
    public <T> T parseSuccess(
            byte[] body,
            Class<T> responseType,
            @Nullable String signature,
            @Nullable String timestamp,
            @Nullable String nonce
    ) {
        String json = verify(
                body,
                signature,
                timestamp,
                nonce
        );
        try {
            T response = jsonCodec.read(json, responseType);
            if (response == null) {
                throw new AlipayProtocolException(
                        "支付宝 V3 成功响应不是预期 JSON 结构"
                );
            }
            return response;
        } catch (JsonException | IllegalArgumentException exception) {
            throw new AlipayProtocolException("支付宝 V3 成功响应不是预期 JSON 结构", exception);
        }
    }

    /**
     * 验签并转换支付宝 V3 错误响应。
     *
     * @param statusCode HTTP 状态码
     * @param verified   是否要求并验证响应签名
     * @param body       原始 UTF-8 响应正文
     * @param signature  {@code alipay-signature} 响应头
     * @param timestamp  {@code alipay-timestamp} 响应头
     * @param nonce      {@code alipay-nonce} 响应头
     * @param traceId    可选支付宝链路标识
     * @return 结构化 API 异常
     */
    public AlipayApiException parseError(
            int statusCode,
            boolean verified,
            byte[] body,
            @Nullable String signature,
            @Nullable String timestamp,
            @Nullable String nonce,
            @Nullable String traceId
    ) {
        String json = verified
                ? verify(
                        body,
                        signature,
                        timestamp,
                        nonce
                )
                : decode(body);
        ErrorPayload error;
        try {
            error = jsonCodec.read(json, ErrorPayload.class);
        } catch (JsonException | IllegalArgumentException exception) {
            throw new AlipayProtocolException("支付宝 V3 错误响应不是有效 JSON", exception);
        }
        if (error == null) {
            throw new AlipayProtocolException("支付宝 V3 错误响应不是有效 JSON");
        }
        // 附属信息只做宽松解析：其结构异常不能掩盖错误码本身
        List<AlipayApiException.Detail> details = error.details == null ? List.of()
                : error.details.stream().filter(Objects::nonNull).toList();
        return new AlipayApiException(
                statusCode,
                verified,
                AlipayValidationUtils.requireResponseText(error.code, "code"),
                AlipayValidationUtils.requireResponseText(error.message, "message"),
                details,
                toLinks(error.links),
                traceId
        );
    }

    /**
     * 判断响应头是否携带任一 V3 签名元数据。
     *
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @return 任一签名元数据存在时返回 {@code true}
     */
    public boolean hasSignatureMetadata(
            @Nullable String signature,
            @Nullable String timestamp,
            @Nullable String nonce
    ) {
        return hasText(signature) || hasText(timestamp) || hasText(nonce);
    }

    /**
     * 使用 V3 响应头和原始正文执行 RSA2 验签。
     *
     * @param body      原始响应正文
     * @param signature 响应签名
     * @param timestamp 响应时间戳
     * @param nonce     响应随机数
     * @return 已验签 UTF-8 正文
     */
    private String verify(
            byte[] body,
            @Nullable String signature,
            @Nullable String timestamp,
            @Nullable String nonce
    ) {
        // 1. 三个 V3 签名响应头必须同时存在，部分缺失按安全失败处理。
        if (!hasText(signature) || !hasText(timestamp) || !hasText(nonce)) {
            throw new AlipaySecurityException(AlipaySecurityFailure.MISSING_SIGNATURE);
        }

        // 2. 使用未经 JSON 重编码的 UTF-8 原始正文构造固定三行验签原文。
        String json = decode(body);
        String source = timestamp + "\n" + nonce + "\n" + json + "\n";

        // 3. 只有 RSA2 验签成功后才把正文交给业务反序列化流程。
        if (!AlipayCryptoUtils.verify(source, signature, alipayPublicKey)) {
            throw new AlipaySecurityException(AlipaySecurityFailure.INVALID_SIGNATURE);
        }
        return json;
    }

    /**
     * 将非空响应正文按 V3 固定 UTF-8 编码转换为文本。
     *
     * @param body 原始响应正文
     * @return UTF-8 文本
     */
    private static String decode(byte[] body) {
        if (body.length == 0) {
            throw new AlipayProtocolException("支付宝 V3 响应缺少正文");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 宽松解析 links：支付宝可能返回单个链接字符串或链接对象数组，无法识别的结构直接忽略。
     */
    private static List<AlipayApiException.Link> toLinks(@Nullable Object value) {
        if (value instanceof String link) {
            return link.isBlank() ? List.of() : List.of(new AlipayApiException.Link(link, null));
        }
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<AlipayApiException.Link> links = new ArrayList<>(values.size());
        for (Object item : values) {
            if (item instanceof Map<?, ?> fields) {
                links.add(new AlipayApiException.Link(
                        fields.get("link") instanceof String link ? link : null,
                        fields.get("rel") instanceof String rel ? rel : null));
            }
        }
        return List.copyOf(links);
    }

    /**
     * 判断协议头是否包含非空文本。
     *
     * @param value 可选协议头
     * @return 包含非空文本时返回 {@code true}
     */
    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    /**
     * OpenAPI V3 标准错误响应载荷。
     *
     * @param code    错误码；缺失或为空时视为协议错误
     * @param message 错误描述；缺失或为空时视为协议错误
     * @param details 可选字段级错误明细；未返回时为 {@code null}
     * @param links   可选解决方案链接，兼容字符串及对象数组两种官方响应形式
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorPayload(
            @JsonProperty("code") @Nullable String code,
            @JsonProperty("message") @Nullable String message,
            @JsonProperty("details") @Nullable List<AlipayApiException.Detail> details,
            @JsonProperty("links") @Nullable Object links
    ) {
    }
}
