/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat;

import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.http.HttpHeaders;
import com.zhengshuyun.lava.pay.wechat.exception.*;
import com.zhengshuyun.lava.pay.wechat.notification.RefundNotification;
import com.zhengshuyun.lava.pay.wechat.notification.TransactionNotification;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证微信支付通知的签名、时效、AES-GCM 解密、商户绑定和业务对账边界。
 */
class NotificationParserTest {
    /**
     * 用于判定通知签名时间窗口的固定 UTC 时钟。
     */
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    /**
     * 通知解密后必须匹配的测试商户号。
     */
    private static final String MCHID = "1900000109";
    /**
     * 用于加密模拟通知资源的 32 字节 APIv3 密钥。
     */
    private static final byte[] API_V3_KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    /**
     * AES-GCM 测试加密使用的固定 12 字节随机量。
     */
    private static final byte[] NONCE = "0123456789ab".getBytes(StandardCharsets.UTF_8);

    /**
     * 构建待测客户端所需的测试商户 RSA 密钥对。
     */
    private static KeyPair merchantKeys;
    /**
     * 通知签名和客户端验签使用的微信支付 RSA 密钥对。
     */
    private static KeyPair wechatKeys;
    /**
     * 为待测客户端提供本地 API 地址的模拟服务端。
     */
    private WechatPayTestServer server;
    /**
     * 持有测试商户号、APIv3 密钥和微信支付公钥的待测客户端。
     */
    private WechatPayClient client;

    /**
     * 为全部通知测试生成一次性 RSA 2048 位商户密钥和微信支付密钥。
     *
     * @throws Exception 当当前 JCA 环境无法创建 RSA 密钥生成器时抛出
     */
    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchantKeys = generator.generateKeyPair();
        wechatKeys = generator.generateKeyPair();
    }

    /**
     * 在每个测试前启动模拟服务端，并构建具有固定时钟和通知密钥的待测客户端。
     */
    @BeforeEach
    void setUp() {
        server = WechatPayTestServer.start(wechatKeys.getPrivate(), CLOCK);
        client = WechatPayClient.builder()
                .mchid(MCHID)
                .merchantPrivateKey(merchantKeys.getPrivate())
                .merchantSerialNo("ABCDEF")
                .apiV3Key(API_V3_KEY)
                .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID)
                .wechatPayPublicKey(wechatKeys.getPublic())
                .apiBaseUrl(server.baseUrl())
                .clock(CLOCK)
                .build();
    }

    /**
     * 在每个测试后关闭待测客户端和本地模拟服务端。
     */
    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    /**
     * 验证交易通知必须先验签再解密，并支持按应用、订单号和金额对账；篡改密文必须报签名无效。
     */
    @Test
    void transactionNotificationIsVerifiedBeforeDecryption() {
        String plaintext = """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","transaction_id":"4200000001",
                 "trade_type":"NATIVE","trade_state":"SUCCESS",
                 "trade_state_desc":"支付成功","bank_type":"OTHERS",
                 "success_time":"2026-08-29T08:00:00+08:00",
                 "payer":{"openid":"openid"},
                 "amount":{"total":100,"payer_total":100,
                 "currency":"CNY","payer_currency":"CNY"}}
                """;
        byte[] body = envelope("TRANSACTION.SUCCESS", "transaction", plaintext);
        HttpHeaders headers = signedHeaders(body, CLOCK.instant().getEpochSecond());

        TransactionNotification notification = client.notifications()
                .parseTransaction(headers, body);

        assertEquals("ORDER_001", notification.transaction().outTradeNo());
        assertEquals("SUCCESS", notification.transaction().tradeState());
        assertSame(notification, notification.requireOrder(
                "wx1234567890",
                "ORDER_001",
                100
        ));
        assertThrows(
                WechatPaySecurityException.class,
                () -> notification.requireOrder(
                        "wx1234567890",
                        "ORDER_001",
                        101
                )
        );

        body[body.length - 2] ^= 1;
        WechatPaySecurityException failure = assertThrows(WechatPaySecurityException.class,
                () -> client.notifications().parseTransaction(headers, body));
        assertEquals(WechatPaySecurityFailure.INVALID_SIGNATURE, failure.failure());
    }

    /**
     * 验证退款通知保留微信支付官方 {@code ABNORMAL} 状态，并在商户退款号或金额不匹配时拒绝对账。
     */
    @Test
    void refundNotificationPreservesOfficialStatus() {
        String plaintext = """
                {"mchid":"1900000109","out_trade_no":"ORDER_001",
                 "transaction_id":"4200000001","out_refund_no":"REFUND_001",
                 "refund_id":"5000000001","refund_status":"ABNORMAL",
                 "user_received_account":"支付用户零钱",
                 "amount":{"total":100,"refund":50,
                 "payer_total":100,"payer_refund":50}}
                """;
        byte[] body = envelope("REFUND.ABNORMAL", "refund", plaintext);

        RefundNotification notification = client.notifications().parseRefund(
                signedHeaders(body, CLOCK.instant().getEpochSecond()), body);

        assertEquals("ABNORMAL", notification.refund().refundStatus());
        assertEquals("REFUND_001", notification.refund().outRefundNo());
        assertSame(notification, notification.requireRefund(
                "ORDER_001",
                "REFUND_001",
                100,
                50
        ));
        assertThrows(
                WechatPaySecurityException.class,
                () -> notification.requireRefund(
                        "ORDER_001",
                        "REFUND_001",
                        100,
                        40
                )
        );
    }

    /**
     * 验证超出 5 分钟时间窗口的通知和解密后商户号不匹配的通知均关闭失败，且安全分类稳定。
     */
    @Test
    void staleNotificationAndMerchantMismatchFailClosed() {
        String plaintext = """
                {"appid":"wx1234567890","mchid":"other-mchid",
                 "out_trade_no":"ORDER_001","transaction_id":"4200000001",
                 "trade_type":"NATIVE","trade_state":"SUCCESS",
                 "trade_state_desc":"支付成功","bank_type":"OTHERS",
                 "success_time":"2026-08-29T08:00:00+08:00",
                 "payer":{"openid":"openid"},
                 "amount":{"total":100,"payer_total":100,
                 "currency":"CNY","payer_currency":"CNY"}}
                """;
        byte[] body = envelope("TRANSACTION.SUCCESS", "transaction", plaintext);

        WechatPaySecurityException stale = assertThrows(WechatPaySecurityException.class,
                () -> client.notifications().parseTransaction(
                        signedHeaders(body, CLOCK.instant().minusSeconds(301).getEpochSecond()),
                        body));
        assertEquals(WechatPaySecurityFailure.EXPIRED_TIMESTAMP, stale.failure());

        WechatPaySecurityException mismatch = assertThrows(WechatPaySecurityException.class,
                () -> client.notifications().parseTransaction(
                        signedHeaders(body, CLOCK.instant().getEpochSecond()), body));
        assertEquals(WechatPaySecurityFailure.MERCHANT_MISMATCH, mismatch.failure());
    }

    /**
     * 验证通知验签通过后，缺失加密资源或解密业务字段不完整时以协议错误终止处理。
     */
    @Test
    void malformedEnvelopeFailsAsProtocolErrorAfterSignatureVerification() {
        byte[] body = """
                {"id":"EV-001","create_time":"2026-08-29T08:00:00+08:00",
                 "resource_type":"encrypt-resource",
                 "event_type":"TRANSACTION.SUCCESS","summary":"测试通知"}
                """.getBytes(StandardCharsets.UTF_8);

        assertThrows(WechatPayProtocolException.class,
                () -> client.notifications().parseTransaction(
                        signedHeaders(body, CLOCK.instant().getEpochSecond()), body));
    }

    /**
     * 验证通知含多个签名头时在密码校验前拒绝，防止不确定的验签头选择。
     */
    @Test
    void duplicateSignatureHeadersAreRejectedBeforeVerification() {
        String plaintext = """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","transaction_id":"4200000001",
                 "trade_type":"NATIVE","trade_state":"SUCCESS",
                 "trade_state_desc":"支付成功","bank_type":"OTHERS",
                 "success_time":"2026-08-29T08:00:00+08:00",
                 "payer":{"openid":"openid"},
                 "amount":{"total":100,"payer_total":100,
                 "currency":"CNY","payer_currency":"CNY"}}
                """;
        byte[] body = envelope("TRANSACTION.SUCCESS", "transaction", plaintext);
        HttpHeaders signed = signedHeaders(body, CLOCK.instant().getEpochSecond());
        HttpHeaders duplicated = HttpHeaders.builder()
                .addAll(signed)
                .add("Wechatpay-Signature", "duplicate")
                .build();

        WechatPaySecurityException failure = assertThrows(
                WechatPaySecurityException.class,
                () -> client.notifications().parseTransaction(duplicated, body)
        );

        assertEquals(
                WechatPaySecurityFailure.DUPLICATE_SIGNATURE_HEADER,
                failure.failure()
        );
    }

    /**
     * 验证不支持的通知加密算法和错误 APIv3 密钥分别映射为稳定的算法不支持与解密失败安全分类。
     */
    @Test
    void unsupportedAlgorithmAndWrongApiV3KeyFailWithStableSecurityCategories() {
        String plaintext = """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","transaction_id":"4200000001",
                 "trade_type":"NATIVE","trade_state":"SUCCESS",
                 "trade_state_desc":"支付成功"}
                """;
        byte[] unsupportedBody = new String(
                envelope("TRANSACTION.SUCCESS", "transaction", plaintext),
                StandardCharsets.UTF_8)
                .replace("AEAD_AES_256_GCM", "UNSUPPORTED")
                .getBytes(StandardCharsets.UTF_8);

        WechatPaySecurityException unsupported = assertThrows(
                WechatPaySecurityException.class,
                () -> client.notifications().parseTransaction(
                        signedHeaders(unsupportedBody, CLOCK.instant().getEpochSecond()),
                        unsupportedBody));
        assertEquals(WechatPaySecurityFailure.UNSUPPORTED_ENCRYPTION_ALGORITHM,
                unsupported.failure());

        byte[] body = envelope("TRANSACTION.SUCCESS", "transaction", plaintext);
        byte[] wrongKey = "fedcba9876543210fedcba9876543210"
                .getBytes(StandardCharsets.UTF_8);
        try (WechatPayClient wrongKeyClient = WechatPayClient.builder()
                .mchid(MCHID)
                .merchantPrivateKey(merchantKeys.getPrivate())
                .merchantSerialNo("ABCDEF")
                .apiV3Key(wrongKey)
                .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID)
                .wechatPayPublicKey(wechatKeys.getPublic())
                .apiBaseUrl(server.baseUrl())
                .clock(CLOCK)
                .build()) {
            WechatPaySecurityException decryption = assertThrows(
                    WechatPaySecurityException.class,
                    () -> wrongKeyClient.notifications().parseTransaction(
                            signedHeaders(body, CLOCK.instant().getEpochSecond()), body));
            assertEquals(WechatPaySecurityFailure.DECRYPTION_FAILED,
                    decryption.failure());
        }
    }

    /**
     * 使用固定 APIv3 密钥和随机量加密业务原文，生成微信支付通知信封 JSON。
     *
     * @param eventType 通知的官方事件类型
     * @param originalType 加密资源的原始业务类型
     * @param plaintext 要放入加密资源的 UTF-8 JSON 原文
     * @return UTF-8 编码的完整通知信封
     */
    private static byte[] envelope(String eventType, String originalType, String plaintext) {
        byte[] ciphertext = CryptoUtils.aesGcmEncrypt(
                API_V3_KEY,
                NONCE,
                new byte[0],
                plaintext.getBytes(StandardCharsets.UTF_8)
        );
        String json = """
                {"id":"EV-001","create_time":"2026-08-29T08:00:00+08:00",
                 "resource_type":"encrypt-resource","event_type":"%s",
                 "summary":"测试通知","resource":{"original_type":"%s",
                 "algorithm":"AEAD_AES_256_GCM","ciphertext":"%s",
                 "associated_data":"","nonce":"%s"}}
                """.formatted(
                eventType,
                originalType,
                Base64.getEncoder().encodeToString(ciphertext),
                new String(NONCE, StandardCharsets.UTF_8)
        );
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 使用微信支付测试私钥为通知正文生成完整验签请求头。
     *
     * @param body 签名覆盖的原始通知正文
     * @param timestamp 签名请求头使用的 Unix 秒级时间戳
     * @return 包含时间戳、随机串、公钥 ID 和 Base64 签名的 HTTP 请求头
     */
    private static HttpHeaders signedHeaders(byte[] body, long timestamp) {
        return WechatPayTestServer.signedHeaders(
                body,
                wechatKeys.getPrivate(),
                WechatPayTestServer.PUBLIC_KEY_ID,
                timestamp
        );
    }
}
