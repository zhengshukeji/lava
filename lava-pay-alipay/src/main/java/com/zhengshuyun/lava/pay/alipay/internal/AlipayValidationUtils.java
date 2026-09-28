/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityFailure;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 支付宝参数与响应校验。
 *
 * <p>请求参数只校验必填项和本 SDK 无法正确发出请求的情况；字段格式、长度等业务规则由支付宝
 * 服务端校验并返回明确错误码，客户端不重复实现。响应侧的一致性校验用于防止错配，必须保留。</p>
 */
public final class AlipayValidationUtils {

    /**
     * 支付宝正式与沙箱 OpenAPI 域名。
     */
    private static final Set<String> OFFICIAL_OPENAPI_HOSTS = Set.of(
            "openapi.alipay.com", "openapi-sandbox.dl.alipaydev.com");

    private AlipayValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验应用 ID 非空白。
     *
     * @param value 应用 ID
     * @return 原值
     */
    public static String requireAppId(String value) {
        return ValidationUtils.requireNotBlank(value, "appId must not be blank");
    }

    /**
     * 校验卖家支付宝用户 ID 非空白。
     *
     * @param value 卖家 ID
     * @return 原值
     */
    public static String requireSellerId(String value) {
        return ValidationUtils.requireNotBlank(value, "sellerId must not be blank");
    }

    /**
     * 校验商户订单号非空白。
     *
     * @param value 商户订单号
     * @return 原值
     */
    public static String requireOutTradeNo(String value) {
        return ValidationUtils.requireNotBlank(value, "outTradeNo must not be blank");
    }

    /**
     * 校验退款请求号非空白。
     *
     * @param value 退款请求号
     * @return 原值
     */
    public static String requireOutRequestNo(String value) {
        return ValidationUtils.requireNotBlank(value, "outRequestNo must not be blank");
    }

    /**
     * 校验支付宝交易号非空白。
     *
     * @param value 支付宝交易号
     * @return 原值
     */
    public static String requireTradeNo(String value) {
        return ValidationUtils.requireNotBlank(value, "tradeNo must not be blank");
    }

    /**
     * 校验以分为单位的金额为正数。
     *
     * @param value 金额，单位为分
     * @param name  参数名称
     * @return 原值
     */
    public static long requirePositiveAmount(long value, String name) {
        ValidationUtils.requireTrue(value > 0, name + " must be positive");
        return value;
    }

    /**
     * 校验回调地址为绝对 HTTP(S) 地址。
     *
     * @param value 回调地址
     * @param name  参数名称
     * @return 原值
     */
    public static URI requireCallbackUrl(URI value, String name) {
        ValidationUtils.requireNonNull(value, name + " must not be null");
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        ValidationUtils.requireTrue(value.isAbsolute() && value.getHost() != null
                        && (scheme.equals("http") || scheme.equals("https")),
                name + " must be an absolute HTTP or HTTPS URI");
        return value;
    }

    /**
     * 校验 OpenAPI 根地址。
     *
     * <p>V3 签名不覆盖主机名，签名请求一旦发往第三方主机即可被中继到真实网关，因此只允许
     * 正式与沙箱域名；环回主机仅用于本地协议测试。根地址不得带路径、查询参数、片段或用户信息。</p>
     *
     * @param value 根地址
     * @return 原值
     */
    public static URI requireBaseUrl(URI value) {
        ValidationUtils.requireNonNull(value, "baseUrl must not be null");
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        String host = value.getHost();
        boolean loopback = host != null && ("localhost".equalsIgnoreCase(host) || host.startsWith("127.")
                || "[::1]".equals(host) || "::1".equals(host));
        boolean official = scheme.equals("https") && host != null
                && OFFICIAL_OPENAPI_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                && (value.getPort() == -1 || value.getPort() == 443);
        boolean localTest = loopback && (scheme.equals("https") || scheme.equals("http"));
        ValidationUtils.requireTrue(value.isAbsolute() && (official || localTest),
                "baseUrl must use an official Alipay HTTPS host or a loopback test host");
        ValidationUtils.requireTrue(value.getUserInfo() == null && value.getRawQuery() == null
                        && value.getRawFragment() == null,
                "baseUrl must not contain user information, query, or fragment");
        ValidationUtils.requireTrue(value.getRawPath() == null || value.getRawPath().isEmpty()
                        || "/".equals(value.getRawPath()),
                "baseUrl must not contain an API path");
        return value;
    }

    /**
     * 校验响应回显字段与请求一致，防止把其他订单或商户的响应当作本次结果。
     *
     * @param expected 请求值
     * @param actual   响应值
     * @throws AlipaySecurityException 两者不一致
     */
    public static void requireSame(String expected, @Nullable String actual) {
        if (!expected.equals(actual)) {
            throw new AlipaySecurityException(AlipaySecurityFailure.RESPONSE_MISMATCH);
        }
    }

    /**
     * 校验响应必填字段存在。
     *
     * @param value 响应字段值
     * @param name  字段名称
     * @return 原值
     * @throws AlipayProtocolException 字段缺失或为空白
     */
    public static String requireResponseText(@Nullable String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AlipayProtocolException("支付宝响应缺少字段 " + name);
        }
        return value;
    }
}
