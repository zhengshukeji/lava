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

import com.zhengshuyun.lava.http.HttpHeaders;
import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.crypto.CryptoException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayProtocolException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityFailure;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 微信支付 APIv3 请求签名、应答验签和回调解密工具。
 */
public final class WechatPayCryptoUtils {
    /** 微信支付 APIv3 RSA-SHA256 鉴权类型。 */
    public static final String AUTHORIZATION_TYPE = "WECHATPAY2-SHA256-RSA2048";
    /** 微信支付通知资源的 AES-GCM 算法标识。 */
    public static final String ENCRYPTION_ALGORITHM = "AEAD_AES_256_GCM";
    /** 微信支付公钥 ID 或平台证书序列号请求头。 */
    public static final String HEADER_SERIAL = "Wechatpay-Serial";
    /** 微信支付消息签名请求头。 */
    public static final String HEADER_SIGNATURE = "Wechatpay-Signature";
    /** 微信支付消息签名时间戳请求头。 */
    public static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    /** 微信支付消息签名随机串请求头。 */
    public static final String HEADER_NONCE = "Wechatpay-Nonce";
    /** 微信支付消息签名类型请求头。 */
    public static final String HEADER_SIGNATURE_TYPE = "Wechatpay-Signature-Type";
    /** 微信支付服务端请求标识响应头。 */
    public static final String HEADER_REQUEST_ID = "Request-ID";
    /**
     * 协议允许的 {@code MAX_TIMESTAMP_SKEW_SECONDS} 最大边界。
     */
    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 5 * 60L;
    /**
     * 微信支付协议使用的 {@code RANDOM} 常量。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 禁止实例化微信支付密码学工具。 */
    private WechatPayCryptoUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成请求使用的 32 位十六进制随机串。
     *
     * @return 随机串
     */
    public static String randomNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 根据最终请求目标和原始正文生成 Authorization 请求头。
     *
     * @param mchid 商户号
     * @param merchantSerialNo 商户 API 证书序列号
     * @param privateKey 商户私钥
     * @param method HTTP 方法
     * @param uri 最终请求 URI
     * @param body 原始请求正文；无正文时传空字节数组
     * @param timestamp Unix 秒时间戳
     * @param nonce 请求随机串
     * @return Authorization 请求头值
     */
    public static String authorization(
            String mchid,
            String merchantSerialNo,
            PrivateKey privateKey,
            String method,
            URI uri,
            byte[] body,
            long timestamp,
            String nonce
    ) {
        // 1. 使用最终请求目标、时间戳、随机串和原始正文构造五行签名原文
        byte[] message = signatureMessage(body, method, requestTarget(uri), Long.toString(timestamp), nonce);
        // 2. 使用商户 API 私钥签名，并按官方固定字段顺序生成 Authorization
        String signature = Base64.getEncoder().encodeToString(sign(privateKey, message));
        return AUTHORIZATION_TYPE
                + " mchid=\"" + mchid + "\""
                + ",nonce_str=\"" + nonce + "\""
                + ",signature=\"" + signature + "\""
                + ",timestamp=\"" + timestamp + "\""
                + ",serial_no=\"" + merchantSerialNo + "\"";
    }

    /**
     * 验证微信支付 API 应答或通知的公钥签名及五分钟时间偏差。
     *
     * @param headers 微信支付签名请求头
     * @param body 未修改的原始正文
     * @param expectedPublicKeyId 当前商户配置的微信支付公钥 ID
     * @param publicKey 微信支付公钥
     * @param clock 当前时钟
     * @throws WechatPaySecurityException 请求头缺失、时间过期或签名无效
     */
    public static void verifyMessage(
            HttpHeaders headers,
            byte[] body,
            String expectedPublicKeyId,
            PublicKey publicKey,
            Clock clock
    ) {
        // 1. 先完整提取签名元数据，任何缺失都不能降级为未验签处理。
        String serial = requiredHeader(headers, HEADER_SERIAL);
        String signature = requiredHeader(headers, HEADER_SIGNATURE);
        String timestampText = requiredHeader(headers, HEADER_TIMESTAMP);
        String nonce = requiredHeader(headers, HEADER_NONCE);
        if (!expectedPublicKeyId.equals(serial)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.UNEXPECTED_PUBLIC_KEY_ID);
        }
        String signatureType = optionalHeader(headers, HEADER_SIGNATURE_TYPE);
        if (signatureType != null && !AUTHORIZATION_TYPE.equals(signatureType)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.UNSUPPORTED_SIGNATURE_TYPE);
        }

        // 2. 在执行昂贵的 RSA 验签前拒绝过期或来自未来的消息，降低重放风险。
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.INVALID_TIMESTAMP, exception);
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < now - MAX_TIMESTAMP_SKEW_SECONDS
                || timestamp > now + MAX_TIMESTAMP_SKEW_SECONDS) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.EXPIRED_TIMESTAMP);
        }

        // 3. 使用原始正文构造三行验签串；验签失败的探测流量按普通失败处理。
        byte[] message = signatureMessage(body, timestampText, nonce);
        byte[] decodedSignature;
        try {
            decodedSignature = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException exception) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.INVALID_SIGNATURE, exception);
        }
        boolean valid;
        try {
            valid = CryptoUtils.rsaSha256Verify(publicKey, message, decodedSignature);
        } catch (CryptoException exception) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.INVALID_SIGNATURE, exception);
        }
        if (!valid) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.INVALID_SIGNATURE);
        }
    }

    /**
     * 解密微信支付回调中的 AEAD_AES_256_GCM 资源。
     *
     * @param apiV3Key 32 字节 APIv3 密钥
     * @param algorithm 回调声明的算法
     * @param nonce GCM 随机串
     * @param associatedData 可选附加数据
     * @param ciphertext Base64 密文和认证标签
     * @return UTF-8 明文 JSON
     */
    public static byte[] decrypt(
            byte[] apiV3Key,
            String algorithm,
            String nonce,
            @Nullable String associatedData,
            String ciphertext
    ) {
        // 1. 只接受微信支付声明的 AES-GCM 算法和完整密文参数。
        if (!ENCRYPTION_ALGORITHM.equals(algorithm)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.UNSUPPORTED_ENCRYPTION_ALGORITHM);
        }
        if (nonce == null || nonce.isBlank() || ciphertext == null
                || ciphertext.isBlank()) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.DECRYPTION_FAILED);
        }
        // 2. 严格解码 Base64 密文，格式错误统一归类为解密安全失败。
        byte[] encrypted;
        try {
            encrypted = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException exception) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.DECRYPTION_FAILED, exception);
        }
        try {
            // 3. 由 AES-GCM 认证标签同时验证密文和附加数据完整性
            return CryptoUtils.aesGcmDecrypt(
                    apiV3Key,
                    nonce.getBytes(StandardCharsets.UTF_8),
                    (associatedData == null ? "" : associatedData).getBytes(StandardCharsets.UTF_8),
                    encrypted);
        } catch (CryptoException | IllegalArgumentException exception) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.DECRYPTION_FAILED, exception);
        }
    }

    /**
     * 返回最终 URI 参与签名的路径和查询串。
     *
     * @param uri 最终请求 URI
     * @return path 与 query 的原始编码文本
     */
    public static String requestTarget(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return uri.getRawQuery() == null ? path : path + '?' + uri.getRawQuery();
    }

    /**
     * 使用商户私钥为已按微信支付规则拼接的原文生成 RSA-SHA256 签名。
     *
     * @param privateKey 商户 API 证书对应的 RSA 私钥
     * @param message    待签名原文字节；内容必须与最终发送请求完全一致
     * @return 原始 RSA 签名字节，调用方可继续进行 Base64 编码
     * @throws WechatPayProtocolException 私钥算法不受支持或底层签名运算失败
     */
    private static byte[] sign(PrivateKey privateKey, byte[] message) {
        try {
            return CryptoUtils.rsaSha256Sign(privateKey, message);
        } catch (CryptoException exception) {
            throw new WechatPayProtocolException("无法生成微信支付请求签名", exception);
        }
    }

    /**
     * 构造微信支付签名原文：每个字段各占一行，最后一行是原始正文，每行以 {@code \n} 结尾。
     */
    private static byte[] signatureMessage(byte[] body, String... lines) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(body.length + 128);
        for (String line : lines) {
            output.writeBytes(line.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
        }
        output.writeBytes(body);
        output.write('\n');
        return output.toByteArray();
    }

    /**
     * 读取必填且只能出现一次的签名头，拒绝代理合并前后的歧义输入。
     *
     * @param headers 原始请求头集合
     * @param name    请求头名称
     * @return 唯一非空请求头值
     */
    private static String requiredHeader(HttpHeaders headers, String name) {
        String value = optionalHeader(headers, name);
        if (value == null || value.isBlank()) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.MISSING_SIGNATURE_HEADER);
        }
        return value;
    }

    /**
     * 读取最多出现一次的签名头。
     *
     * @param headers 原始请求头集合
     * @param name    请求头名称
     * @return 请求头值；没有时为 {@code null}
     */
    private static @Nullable String optionalHeader(HttpHeaders headers, String name) {
        List<String> values = headers.values(name);
        if (values.size() > 1) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.DUPLICATE_SIGNATURE_HEADER);
        }
        return values.isEmpty() ? null : values.getFirst();
    }
}
