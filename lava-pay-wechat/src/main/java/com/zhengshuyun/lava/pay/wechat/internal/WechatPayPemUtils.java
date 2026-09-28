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

package com.zhengshuyun.lava.pay.wechat.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.crypto.CryptoException;
import com.zhengshuyun.lava.crypto.PemKeyUtils;
import com.zhengshuyun.lava.crypto.RsaSignatureUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;
import java.util.Locale;

/**
 * 微信支付商户私钥、微信支付公钥与商户证书的读取和校验。
 */
public final class WechatPayPemUtils {

    /**
     * APIv3 签名要求的最小 RSA 模长。
     */
    private static final int MIN_RSA_BITS = 2048;

    private WechatPayPemUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从 PEM 文件读取商户 API 私钥。
     *
     * @param path 私钥文件
     * @return RSA 私钥
     * @throws IllegalArgumentException 文件不可读或内容不是有效的 RSA2048 私钥
     */
    public static PrivateKey readPrivateKey(Path path) {
        try {
            return requirePrivateKey(PemKeyUtils.readRsaPrivateKey(readText(path, "merchantPrivateKey")));
        } catch (CryptoException exception) {
            throw new IllegalArgumentException("merchantPrivateKey is not a valid PKCS#8 RSA key", exception);
        }
    }

    /**
     * 从 PEM 文件读取微信支付公钥。
     *
     * @param path 公钥文件
     * @return RSA 公钥
     * @throws IllegalArgumentException 文件不可读或内容不是有效的 RSA2048 公钥
     */
    public static PublicKey readPublicKey(Path path) {
        try {
            return requirePublicKey(PemKeyUtils.readRsaPublicKey(readText(path, "wechatPayPublicKey")));
        } catch (CryptoException exception) {
            throw new IllegalArgumentException("wechatPayPublicKey is not a valid X.509 RSA key", exception);
        }
    }

    /**
     * 从 PEM 文件读取商户 API 证书，并校验其在有效期内。
     *
     * @param path 证书文件
     * @return 商户证书
     * @throws IllegalArgumentException 文件不可读、不是 X.509 证书或证书已过期
     */
    public static X509Certificate readCertificate(Path path) {
        ValidationUtils.requireNonNull(path, "merchantCertificate path must not be null");
        try (InputStream input = Files.newInputStream(path)) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            return requireMerchantCertificate(certificate);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("merchantCertificate is not a valid X.509 certificate: " + path,
                    exception);
        }
    }

    /**
     * 返回证书序列号的大写十六进制形式，即请求签名中的 {@code serial_no}。
     *
     * @param certificate 商户证书
     * @return 证书序列号
     */
    public static String serialNo(X509Certificate certificate) {
        return ValidationUtils.requireNonNull(certificate, "certificate")
                .getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
    }

    /**
     * 校验商户私钥为至少 2048 位的 RSA 密钥。
     *
     * @param privateKey 私钥
     * @return 原值
     */
    public static PrivateKey requirePrivateKey(PrivateKey privateKey) {
        requireRsa2048(ValidationUtils.requireNonNull(privateKey, "merchantPrivateKey must not be null"),
                "merchantPrivateKey");
        return privateKey;
    }

    /**
     * 校验微信支付公钥为至少 2048 位的 RSA 密钥。
     *
     * @param publicKey 公钥
     * @return 原值
     */
    public static PublicKey requirePublicKey(PublicKey publicKey) {
        requireRsa2048(ValidationUtils.requireNonNull(publicKey, "wechatPayPublicKey must not be null"),
                "wechatPayPublicKey");
        return publicKey;
    }

    /**
     * 校验商户证书使用 RSA2048 公钥且在有效期内。
     *
     * @param certificate 商户证书
     * @return 原值
     */
    public static X509Certificate requireMerchantCertificate(X509Certificate certificate) {
        ValidationUtils.requireNonNull(certificate, "merchantCertificate must not be null");
        requireRsa2048(certificate.getPublicKey(), "merchantCertificate");
        try {
            certificate.checkValidity();
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("merchantCertificate must be currently valid", exception);
        }
        return certificate;
    }

    /**
     * 用私钥签名一段探测数据再用证书公钥验签，确认两者配对，避免上线后每个请求都签名失败。
     *
     * @param privateKey  商户私钥
     * @param certificate 商户证书
     * @throws IllegalArgumentException 私钥与证书不匹配
     */
    public static void requireKeyPair(PrivateKey privateKey, X509Certificate certificate) {
        byte[] probe = "lava-pay-wechat".getBytes(StandardCharsets.US_ASCII);
        try {
            byte[] signature = RsaSignatureUtils.sha256(privateKey, probe);
            ValidationUtils.requireTrue(RsaSignatureUtils.verifySha256(certificate.getPublicKey(), probe, signature),
                    "merchantPrivateKey does not match merchantCertificate");
        } catch (CryptoException exception) {
            throw new IllegalArgumentException("could not validate merchant key pair", exception);
        }
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
