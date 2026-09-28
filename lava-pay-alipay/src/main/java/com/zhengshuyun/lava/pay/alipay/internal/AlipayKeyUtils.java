/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.crypto.CryptoException;
import com.zhengshuyun.lava.crypto.PemKeyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;

/**
 * 支付宝 RSA2 原始 Base64、PEM 与 JCA 密钥解析工具。
 */
public final class AlipayKeyUtils {

    /**
     * RSA2 签名要求的最小模长。
     */
    private static final int MIN_RSA_BITS = 2048;

    private AlipayKeyUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 读取应用私钥，接受 PKCS#8 PEM 或支付宝开放平台导出的裸 Base64。
     *
     * @param value 私钥文本
     * @return RSA 私钥
     * @throws IllegalArgumentException 文本为空白或不是有效的 RSA2048 私钥
     */
    public static PrivateKey readPrivateKey(String value) {
        ValidationUtils.requireNotBlank(value, "appPrivateKey must not be blank");
        try {
            return requirePrivateKey(PemKeyUtils.readRsaPrivateKey(value));
        } catch (CryptoException exception) {
            throw new IllegalArgumentException("appPrivateKey is not a valid PKCS#8 RSA key", exception);
        }
    }

    /**
     * 从文件读取应用私钥。
     *
     * @param path 私钥文件
     * @return RSA 私钥
     * @throws IllegalArgumentException 文件不可读或内容不是有效的 RSA2048 私钥
     */
    public static PrivateKey readPrivateKey(Path path) {
        return readPrivateKey(readText(path, "appPrivateKey"));
    }

    /**
     * 读取支付宝公钥，接受 X.509 PEM 或支付宝开放平台导出的裸 Base64。
     *
     * @param value 公钥文本
     * @return RSA 公钥
     * @throws IllegalArgumentException 文本为空白或不是有效的 RSA2048 公钥
     */
    public static PublicKey readPublicKey(String value) {
        ValidationUtils.requireNotBlank(value, "alipayPublicKey must not be blank");
        try {
            return requirePublicKey(PemKeyUtils.readRsaPublicKey(value));
        } catch (CryptoException exception) {
            throw new IllegalArgumentException("alipayPublicKey is not a valid X.509 RSA key", exception);
        }
    }

    /**
     * 从文件读取支付宝公钥。
     *
     * @param path 公钥文件
     * @return RSA 公钥
     * @throws IllegalArgumentException 文件不可读或内容不是有效的 RSA2048 公钥
     */
    public static PublicKey readPublicKey(Path path) {
        return readPublicKey(readText(path, "alipayPublicKey"));
    }

    /**
     * 校验应用私钥为至少 2048 位的 RSA 密钥。
     *
     * @param value 私钥
     * @return 原值
     */
    public static PrivateKey requirePrivateKey(PrivateKey value) {
        requireRsa2048(ValidationUtils.requireNonNull(value, "appPrivateKey must not be null"), "appPrivateKey");
        return value;
    }

    /**
     * 校验支付宝公钥为至少 2048 位的 RSA 密钥。
     *
     * @param value 公钥
     * @return 原值
     */
    public static PublicKey requirePublicKey(PublicKey value) {
        requireRsa2048(ValidationUtils.requireNonNull(value, "alipayPublicKey must not be null"), "alipayPublicKey");
        return value;
    }

    private static String readText(Path path, String name) {
        ValidationUtils.requireNonNull(path, name + " path must not be null");
        try {
            return Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            throw new IllegalArgumentException("could not read " + name + " file: " + path, exception);
        }
    }

    private static void requireRsa2048(Key key, String name) {
        ValidationUtils.requireTrue("RSA".equalsIgnoreCase(key.getAlgorithm()), name + " must use RSA");
        // 硬件密钥等场景可能不暴露模长，此时交由签名阶段校验
        if (key instanceof RSAKey rsaKey) {
            ValidationUtils.requireTrue(rsaKey.getModulus().bitLength() >= MIN_RSA_BITS,
                    name + " must use at least RSA2048");
        }
    }
}
