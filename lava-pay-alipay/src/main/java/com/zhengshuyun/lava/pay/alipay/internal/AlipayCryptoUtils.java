/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.zhengshuyun.lava.crypto.CryptoException;
import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityFailure;

import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝 RSA2 签名与验签工具。
 *
 * <p>OpenAPI V3 对固定换行原文签名；页面支付和 From 蚂蚁消息通知仍按 AOP 参数排序规则签名。</p>
 */
public final class AlipayCryptoUtils {
    /** 支付宝 RSA-SHA256 签名类型。 */
    public static final String SIGN_TYPE = "RSA2";

    /** 禁止实例化支付宝密码学工具。 */
    private AlipayCryptoUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 对参数原值按参数名字典序拼接后生成 RSA2 签名。
     *
     * @param params     未 URL 编码的参数
     * @param privateKey 应用私钥
     * @return Base64 签名
     */
    public static String sign(Map<String, String> params, PrivateKey privateKey) {
        return sign(signatureContent(params), privateKey);
    }

    /**
     * 对已经按协议构造完成的原文生成 RSA2 签名。
     *
     * @param source     未编码签名原文
     * @param privateKey 应用私钥
     * @return Base64 签名
     */
    public static String sign(String source, PrivateKey privateKey) {
        byte[] content = source.getBytes(StandardCharsets.UTF_8);
        try {
            return Base64.getEncoder().encodeToString(
                    CryptoUtils.rsaSha256Sign(privateKey, content));
        } catch (CryptoException exception) {
            throw new AlipayProtocolException("无法生成支付宝 RSA2 请求签名", exception);
        }
    }

    /**
     * 验证明确给出的签名原文。
     *
     * @param source    原文
     * @param signature Base64 签名
     * @param publicKey 支付宝公钥
     * @return 是否有效
     */
    public static boolean verify(String source, String signature, PublicKey publicKey) {
        return verify(
                source,
                signature,
                publicKey,
                StandardCharsets.UTF_8
        );
    }

    /**
     * 使用指定字符集验证明确给出的签名原文。
     *
     * @param source    原文
     * @param signature Base64 签名
     * @param publicKey 支付宝公钥
     * @param charset   原文字符集
     * @return 是否有效
     */
    public static boolean verify(
            String source,
            String signature,
            PublicKey publicKey,
            Charset charset
    ) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        try {
            return CryptoUtils.rsaSha256Verify(
                    publicKey,
                    source.getBytes(charset),
                    decoded
            );
        } catch (CryptoException exception) {
            return false;
        }
    }

    /**
     * 按支付宝 V1 规则验证通知参数，排除 {@code sign} 与 {@code sign_type}。
     *
     * @param params    已完成一次表单 URL 解码的参数
     * @param publicKey 支付宝公钥
     */
    public static void verifyNotification(Map<String, String> params, PublicKey publicKey) {
        // 1. 签名和签名类型必须完整且明确，禁止缺失时降级为未验签通知。
        String signature = params.get("sign");
        if (signature == null || signature.isBlank()) {
            throw new AlipaySecurityException(AlipaySecurityFailure.MISSING_SIGNATURE);
        }
        if (!SIGN_TYPE.equals(params.get("sign_type"))) {
            throw new AlipaySecurityException(
                    AlipaySecurityFailure.UNSUPPORTED_SIGNATURE_TYPE);
        }

        // 2. 只接受支付宝通知协议允许的字符集，避免签名原文字节存在歧义。
        Charset charset = notificationCharset(params.get("charset"));

        // 3. 按 V1 规则剔除签名元数据并排序拼接原始表单值，再执行 RSA2 验签。
        Map<String, String> contentParams = new LinkedHashMap<>(params);
        contentParams.remove("sign");
        contentParams.remove("sign_type");
        if (!verify(
                signatureContent(contentParams),
                signature,
                publicKey,
                charset
        )) {
            throw new AlipaySecurityException(AlipaySecurityFailure.INVALID_SIGNATURE);
        }
    }

    /**
     * 构造未 URL 编码的支付宝签名原文。
     *
     * @param params 参数集合
     * @return 签名原文
     */
    public static String signatureContent(Map<String, String> params) {
        List<String> names = new ArrayList<>(params.keySet());
        Collections.sort(names);
        StringBuilder content = new StringBuilder();
        for (String name : names) {
            String value = params.get(name);
            if (name == null || name.isEmpty() || value == null || value.isEmpty()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append('&');
            }
            content.append(name).append('=').append(value);
        }
        return content.toString();
    }

    /**
     * 将通知字符集限制在支付宝协议允许的范围内。
     *
     * @param value 通知声明的字符集
     * @return 可用于验签原文编码的字符集
     */
    private static Charset notificationCharset(String value) {
        String name = value == null || value.isBlank() ? "UTF-8" : value;
        if (!"UTF-8".equalsIgnoreCase(name) && !"GBK".equalsIgnoreCase(name)
                && !"GB2312".equalsIgnoreCase(name)) {
            throw new AlipaySecurityException(
                    AlipaySecurityFailure.UNSUPPORTED_CHARSET);
        }
        try {
            return Charset.forName(name);
        } catch (UnsupportedCharsetException exception) {
            throw new AlipaySecurityException(
                    AlipaySecurityFailure.UNSUPPORTED_CHARSET, exception);
        }
    }
}
