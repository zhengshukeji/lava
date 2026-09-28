/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat;

import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.HttpRequest;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.pay.wechat.exception.*;
import com.zhengshuyun.lava.pay.wechat.h5.H5PrepayResponse;
import com.zhengshuyun.lava.pay.wechat.jsapi.JsapiPayParams;
import com.zhengshuyun.lava.pay.wechat.jsapi.JsapiPrepayResponse;
import com.zhengshuyun.lava.pay.wechat.nativepay.NativePrepayResponse;
import com.zhengshuyun.lava.pay.wechat.prepay.H5Type;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayDetail;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayRequest;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepaySceneInfo;
import com.zhengshuyun.lava.pay.wechat.refund.Refund;
import com.zhengshuyun.lava.pay.wechat.refund.RefundRequest;
import com.zhengshuyun.lava.pay.wechat.transaction.TradeState;
import com.zhengshuyun.lava.pay.wechat.transaction.Transaction;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证微信支付 APIv3 客户端的请求签名、响应验签、业务映射和资源生命周期。
 */
class WechatPayClientTest {
    /**
     * 用于生成可重复请求签名和响应时效校验的固定 UTC 时钟。
     */
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    /**
     * 测试请求和响应对账使用的固定商户号。
     */
    private static final String MCHID = "1900000109";
    /**
     * Native 支付应用上下文注入的固定应用 ID。
     */
    private static final String APPID = "wx1234567890";
    /**
     * 构建客户端使用的 32 字节 APIv3 密钥字符串。
     */
    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    /**
     * 用于精确重建并校验请求签名串的固定随机串。
     */
    private static final String REQUEST_NONCE = "0123456789abcdef0123456789abcdef";
    /**
     * 从 HTTP Authorization 头中提取 Base64 签名值的正则表达式。
     */
    private static final Pattern SIGNATURE = Pattern.compile("signature=\"([^\"]+)\"");

    /**
     * 客户端请求签名和测试断言验签使用的商户 RSA 密钥对。
     */
    private static KeyPair merchantKeys;
    /**
     * 模拟响应签名和客户端验签使用的微信支付 RSA 密钥对。
     */
    private static KeyPair wechatKeys;

    /**
     * 每个测试独占的本地微信支付协议模拟服务端。
     */
    private WechatPayTestServer server;
    /**
     * 指向本地模拟服务端的待测微信支付根客户端。
     */
    private WechatPayClient client;

    /**
     * 为全部客户端测试生成一次性 RSA 2048 位商户密钥和微信支付密钥。
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
     * 在每个测试前启动独立模拟服务端，并构建具有固定时钟和随机串的待测客户端。
     */
    @BeforeEach
    void setUp() {
        server = WechatPayTestServer.start(wechatKeys.getPrivate(), CLOCK);
        client = clientBuilder().build();
    }

    /**
     * 创建已填充完整商户凭据、本地 API 地址和确定性协议参数的客户端 Builder。
     *
     * @return 尚未执行构建、可由单个测试继续定制的 Builder
     */
    private WechatPayClient.Builder clientBuilder() {
        return WechatPayClient.builder()
                .mchid(MCHID)
                .merchantPrivateKey(merchantKeys.getPrivate())
                .merchantSerialNo("ABCDEF0123456789")
                .apiV3Key(API_V3_KEY)
                .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID)
                .wechatPayPublicKey(wechatKeys.getPublic())
                .apiBaseUrl(server.baseUrl())
                .clock(CLOCK)
                .nonceSupplier(() -> REQUEST_NONCE);
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
     * 验证 Native 预支付自动注入应用 ID、商户号和通知地址，并对实际发送的 JSON 正文精确签名。
     */
    @Test
    void nativePrepayInjectsApplicationConfigurationAndSignsExactBody() {
        server.enqueueSigned(200,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=test\"}");
        PrepayRequest request = PrepayRequest.builder()
                .description("测试订单")
                .outTradeNo("ORDER_001")
                .amount(100)
                .profitSharing(false)
                .detail(PrepayDetail.builder()
                        .costPrice(100)
                        .build())
                .build();

        NativePrepayResponse response = client
                .application(APPID, "https://example.com/pay/notify")
                .nativePay().prepay(request);

        assertEquals("weixin", response.codeUrl().getScheme());
        WechatPayTestServer.CapturedRequest captured = server.takeRequest();
        assertEquals("POST", captured.method());
        assertEquals("/v3/pay/transactions/native", captured.target());
        JsonNode body = JsonCodec.defaultCodec().readTree(
                new String(captured.body(), StandardCharsets.UTF_8));
        assertEquals(APPID, body.get("appid").stringValue());
        assertEquals(MCHID, body.get("mchid").stringValue());
        assertEquals("https://example.com/pay/notify",
                body.get("notify_url").stringValue());
        assertEquals(100, body.get("amount").get("total").asInt());
        assertFalse(body.has("time_expire"));
        assertFalse(body.has("attach"));
        assertFalse(body.get("detail").has("invoice_id"));
        assertFalse(body.get("detail").has("goods_detail"));
        assertTrue(verifyRequestSignature(captured));
        assertEquals(WechatPayTestServer.PUBLIC_KEY_ID,
                captured.header("Wechatpay-Serial"));
    }

    /**
     * 验证支付截止时间按协议格式精确到秒输出：纳秒精度的时间不得带小数秒，UTC 偏移不得写成 Z。
     */
    @Test
    void nativePrepayFormatsTimeExpireToSeconds() {
        server.enqueueSigned(200,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=test\"}");
        server.enqueueSigned(200,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=test\"}");
        var nativePay = client.application(APPID, "https://example.com/pay/notify").nativePay();
        Instant expireAt = Instant.parse("2026-09-28T07:23:39.505123456Z");

        nativePay.prepay(PrepayRequest.builder()
                .description("测试订单")
                .outTradeNo("ORDER_001")
                .amount(100)
                .timeExpire(expireAt.atOffset(ZoneOffset.ofHours(8)))
                .build());
        nativePay.prepay(PrepayRequest.builder()
                .description("测试订单")
                .outTradeNo("ORDER_002")
                .amount(100)
                .timeExpire(expireAt.atOffset(ZoneOffset.UTC))
                .build());

        JsonNode east8 = JsonCodec.defaultCodec().readTree(
                new String(server.takeRequest().body(), StandardCharsets.UTF_8));
        assertEquals("2026-09-28T15:23:39+08:00", east8.get("time_expire").stringValue());
        JsonNode utc = JsonCodec.defaultCodec().readTree(
                new String(server.takeRequest().body(), StandardCharsets.UTF_8));
        assertEquals("2026-09-28T07:23:39+00:00", utc.get("time_expire").stringValue());
    }

    /**
     * 验证 JSAPI 下单携带 payer.openid，并按「appId、时间戳、随机串、package」逐行签名生成调起支付参数。
     */
    @Test
    void jsapiPrepayWithRequestPaymentSignsFrontendParameters() {
        server.enqueueSigned(200, "{\"prepay_id\":\"wx2026082900000001\"}");

        JsapiPayParams params = client
                .application(APPID, "https://example.com/pay/notify")
                .jsapiPay().prepayWithRequestPayment("o-openid-001", PrepayRequest.builder()
                        .description("测试订单")
                        .outTradeNo("ORDER_001")
                        .amount(100)
                        .build());

        WechatPayTestServer.CapturedRequest captured = server.takeRequest();
        assertEquals("/v3/pay/transactions/jsapi", captured.target());
        JsonNode body = JsonCodec.defaultCodec().readTree(
                new String(captured.body(), StandardCharsets.UTF_8));
        assertEquals("o-openid-001", body.get("payer").get("openid").stringValue());
        assertEquals(APPID, body.get("appid").stringValue());
        assertTrue(verifyRequestSignature(captured));

        assertEquals(APPID, params.appId());
        assertEquals(Long.toString(CLOCK.instant().getEpochSecond()), params.timeStamp());
        assertEquals(REQUEST_NONCE, params.nonceStr());
        assertEquals("prepay_id=wx2026082900000001", params.packageValue());
        assertEquals("RSA", params.signType());
        String message = APPID + '\n' + params.timeStamp() + '\n' + params.nonceStr() + '\n'
                + params.packageValue() + '\n';
        assertTrue(CryptoUtils.rsaSha256Verify(merchantKeys.getPublic(),
                message.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(params.paySign())));

        // 前端直接消费 JSON，package 字段名必须保持官方拼写
        JsonNode json = JsonCodec.defaultCodec().readTree(JsonCodec.defaultCodec().write(params));
        assertEquals("prepay_id=wx2026082900000001", json.get("package").stringValue());
        assertFalse(json.has("packageValue"));
    }

    /**
     * 验证 JSAPI 下单拒绝空白 OpenID，且响应缺少 prepay_id 时判定为协议错误。
     */
    @Test
    void jsapiPrepayRejectsResponseWithoutPrepayId() {
        server.enqueueSigned(200, "{}");
        var jsapiPay = client.application(APPID, "https://example.com/pay/notify").jsapiPay();

        assertThrows(IllegalArgumentException.class, () -> jsapiPay.prepay(" ",
                PrepayRequest.builder().description("测试订单").outTradeNo("ORDER_001")
                        .amount(100).build()));
        assertThrows(WechatPayProtocolException.class, () -> jsapiPay.prepay("o-openid-001",
                PrepayRequest.builder().description("测试订单").outTradeNo("ORDER_001")
                        .amount(100).build()));
    }

    /**
     * 验证 H5 下单输出 scene_info.h5_info、不输出 payer，并返回 h5_url。
     */
    @Test
    void h5PrepaySendsSceneInfoAndReturnsH5Url() {
        server.enqueueSigned(200,
                "{\"h5_url\":\"https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=wx01\"}");

        H5PrepayResponse response = client
                .application(APPID, "https://example.com/pay/notify")
                .h5Pay().prepay(PrepayRequest.builder()
                        .description("测试订单")
                        .outTradeNo("ORDER_001")
                        .amount(100)
                        .sceneInfo(PrepaySceneInfo.builder()
                                .payerClientIp("203.0.113.8")
                                .h5Info(PrepaySceneInfo.H5Info.of(H5Type.WAP))
                                .build())
                        .build());

        assertEquals("wx.tenpay.com", response.h5Url().getHost());
        WechatPayTestServer.CapturedRequest captured = server.takeRequest();
        assertEquals("/v3/pay/transactions/h5", captured.target());
        JsonNode body = JsonCodec.defaultCodec().readTree(
                new String(captured.body(), StandardCharsets.UTF_8));
        assertFalse(body.has("payer"));
        JsonNode sceneInfo = body.get("scene_info");
        assertEquals("203.0.113.8", sceneInfo.get("payer_client_ip").stringValue());
        assertEquals("Wap", sceneInfo.get("h5_info").get("type").stringValue());
        assertFalse(sceneInfo.get("h5_info").has("app_name"));
        assertTrue(verifyRequestSignature(captured));
    }

    /**
     * 验证 H5 下单在发送请求前拒绝缺少场景信息或 H5 场景信息的请求。
     */
    @Test
    void h5PrepayRequiresH5SceneInfo() {
        var h5Pay = client.application(APPID, "https://example.com/pay/notify").h5Pay();

        assertThrows(IllegalArgumentException.class, () -> h5Pay.prepay(PrepayRequest.builder()
                .description("测试订单").outTradeNo("ORDER_001").amount(100).build()));
        assertThrows(IllegalArgumentException.class, () -> h5Pay.prepay(PrepayRequest.builder()
                .description("测试订单").outTradeNo("ORDER_001").amount(100)
                .sceneInfo(PrepaySceneInfo.builder().payerClientIp("203.0.113.8").build())
                .build()));
        assertEquals(0, server.requestCount());
    }

    /**
     * 验证商户订单号查询和关单分别使用官方 APIv3 路径、HTTP 方法和商户号参数。
     */
    @Test
    void transactionQueryAndCloseUseOfficialPaths() {
        String transaction = """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","trade_state":"NOTPAY",
                 "trade_state_desc":"未支付","future_field":"kept-compatible"}
                """;
        server.enqueueSigned(200, transaction);
        server.enqueueSigned(204, "");

        Transaction result = client.transactions().queryByOutTradeNo("ORDER_001");
        client.transactions().close("ORDER_001");

        assertEquals(TradeState.NOTPAY, result.tradeState());
        WechatPayTestServer.CapturedRequest query = server.takeRequest();
        assertEquals("GET", query.method());
        assertEquals("/v3/pay/transactions/out-trade-no/ORDER_001?mchid=1900000109",
                query.target());
        assertTrue(verifyRequestSignature(query));
        WechatPayTestServer.CapturedRequest close = server.takeRequest();
        assertEquals("/v3/pay/transactions/out-trade-no/ORDER_001/close",
                close.target());
        assertTrue(verifyRequestSignature(close));
    }

    /**
     * 验证已关闭交易的查单响应不含金额时，订单核对只校验应用和商户订单号。
     */
    @Test
    void closedTransactionWithoutAmountPassesOrderCheck() {
        server.enqueueSigned(200, """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","trade_state":"CLOSED",
                 "trade_state_desc":"订单已关闭"}
                """);

        Transaction result = client.transactions().queryByOutTradeNo("ORDER_001");

        assertSame(result, result.requireOrder(APPID, "ORDER_001", 100));
    }

    /**
     * 验证未付款交易一旦返回金额，仍必须与可信订单金额一致。
     */
    @Test
    void unpaidTransactionWithMismatchedAmountFailsOrderCheck() {
        server.enqueueSigned(200, """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","trade_state":"NOTPAY",
                 "trade_state_desc":"未支付",
                 "amount":{"total":200,"currency":"CNY","payer_currency":"CNY"}}
                """);

        Transaction result = client.transactions().queryByOutTradeNo("ORDER_001");

        WechatPaySecurityException failure = assertThrows(
                WechatPaySecurityException.class,
                () -> result.requireOrder(APPID, "ORDER_001", 100));
        assertEquals(WechatPaySecurityFailure.RESPONSE_MISMATCH, failure.failure());
    }

    /**
     * 验证微信支付订单号查询使用官方路径，并将返回交易映射为统一领域模型。
     */
    @Test
    void transactionIdQueryUsesOfficialPath() {
        server.enqueueSigned(200, """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_001","transaction_id":"4200000001",
                 "trade_type":"NATIVE","trade_state":"SUCCESS",
                 "trade_state_desc":"支付成功","bank_type":"OTHERS",
                 "success_time":"2026-08-29T08:00:00+08:00",
                 "payer":{"openid":"openid"},
                 "amount":{"total":100,"payer_total":100,
                 "currency":"CNY","payer_currency":"CNY"}}
                """);

        Transaction result = client.transactions()
                .queryByTransactionId("4200000001");

        assertEquals("4200000001", result.transactionId());
        assertSame(result, result.requireOrder(APPID, "ORDER_001", 100));
        WechatPayTestServer.CapturedRequest query = server.takeRequest();
        assertEquals("/v3/pay/transactions/id/4200000001?mchid=1900000109",
                query.target());
        assertTrue(verifyRequestSignature(query));
    }

    /**
     * 验证退款申请和查询共用强类型退款模型，保留状态、金额和商户业务标识。
     */
    @Test
    void refundApplyAndQueryShareTypedRefundModel() {
        String refund = refundJson();
        server.enqueueSigned(200, refund);
        server.enqueueSigned(200, refund);
        RefundRequest request = RefundRequest.builder()
                .outTradeNo("ORDER_001")
                .outRefundNo("REFUND_001")
                .amount(50, 100)
                .reason("用户取消")
                .notifyUrl("https://example.com/refund/notify")
                .build();

        Refund applied = client.refunds().apply(request);
        Refund queried = client.refunds().queryByOutRefundNo("REFUND_001");

        assertEquals("REFUND_001", applied.outRefundNo());
        assertEquals(applied.refundId(), queried.refundId());
        assertSame(applied, applied.requireRefund(
                "ORDER_001",
                "REFUND_001",
                100,
                50
        ));
        assertSame(queried, queried.requireRefund(
                "ORDER_001",
                "REFUND_001",
                100,
                50
        ));
        WechatPayTestServer.CapturedRequest applyRequest = server.takeRequest();
        assertEquals("/v3/refund/domestic/refunds", applyRequest.target());
        JsonNode applyBody = JsonCodec.defaultCodec().readTree(
                new String(applyRequest.body(), StandardCharsets.UTF_8));
        assertFalse(applyBody.has("transaction_id"));
        assertEquals("https://example.com/refund/notify",
                applyBody.get("notify_url").stringValue());
        assertFalse(applyBody.get("amount").has("from"));
        assertEquals("/v3/refund/domestic/refunds/REFUND_001",
                server.takeRequest().target());
    }

    /**
     * 验证已验签的 API 错误被解析为结构化异常，保留错误码、请求 ID 和字段信息，但不泄露原始响应。
     */
    @Test
    void signedApiErrorIsStructuredAndRawBodyIsNotInExceptionText() {
        server.enqueueSigned(400, """
                {"code":"PARAM_ERROR","message":"参数错误",
                 "detail":{"field":"/amount/total","value":"secret-value",
                 "issue":"must be positive","location":"body"}}
                """);

        WechatPayApiException failure = assertThrows(WechatPayApiException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001"));

        assertEquals(400, failure.statusCode());
        assertTrue(failure.verified());
        assertEquals("PARAM_ERROR", failure.code());
        assertEquals("request-id-001", failure.requestId());
        assertEquals("/amount/total", failure.detail().field());
        assertFalse(failure.toString().contains("secret-value"));
    }

    /**
     * 验证网关层未签名的错误（如请求签名错误）保留真实错误码，并标记为未验签。
     */
    @Test
    void unsignedApiErrorIsStructuredAsUnverified() {
        server.enqueueUnsigned(401, """
                {"code":"SIGN_ERROR","message":"签名错误"}
                """.getBytes(StandardCharsets.UTF_8), "application/json");

        WechatPayApiException failure = assertThrows(WechatPayApiException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001"));

        assertEquals(401, failure.statusCode());
        assertFalse(failure.verified());
        assertEquals("SIGN_ERROR", failure.code());
    }

    /**
     * 验证响应公钥 ID 与配置不匹配时关闭失败，不使用未信任密钥继续验签。
     */
    @Test
    void responseWithUnexpectedPublicKeyIdFailsClosed() {
        server.enqueueSigned(
                200,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=test\"}"
                        .getBytes(StandardCharsets.UTF_8),
                "PUB_KEY_ID_999",
                wechatKeys.getPrivate(),
                CLOCK.instant().getEpochSecond()
        );

        WechatPaySecurityException failure = assertThrows(WechatPaySecurityException.class,
                () -> client.application(APPID, "https://example.com/pay/notify")
                        .nativePay().prepay(PrepayRequest.builder()
                                .description("测试")
                                .outTradeNo("ORDER_001")
                                .amount(1)
                                .build()));

        assertEquals(WechatPaySecurityFailure.UNEXPECTED_PUBLIC_KEY_ID,
                failure.failure());
    }

    /**
     * 验证响应签名不能由已配置微信支付公钥通过时关闭失败。
     */
    @Test
    void responseWithInvalidSignatureFailsClosed() {
        server.enqueueSigned(
                200,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=test\"}"
                        .getBytes(StandardCharsets.UTF_8),
                WechatPayTestServer.PUBLIC_KEY_ID,
                merchantKeys.getPrivate(),
                CLOCK.instant().getEpochSecond()
        );

        WechatPaySecurityException failure = assertThrows(WechatPaySecurityException.class,
                () -> client.application(APPID, "https://example.com/pay/notify")
                        .nativePay().prepay(PrepayRequest.builder()
                                .description("测试")
                                .outTradeNo("ORDER_001")
                                .amount(1)
                                .build()));

        assertEquals(WechatPaySecurityFailure.INVALID_SIGNATURE, failure.failure());
    }

    /**
     * 验证签名正确但成功响应 JSON 结构非法时抛出协议异常，而非安全或 API 异常。
     */
    @Test
    void signedMalformedSuccessResponseUsesProtocolException() {
        server.enqueueSigned(200, "{}");

        assertThrows(WechatPayProtocolException.class,
                () -> client.application(APPID, "https://example.com/pay/notify")
                        .nativePay().prepay(PrepayRequest.builder()
                                .description("测试")
                                .outTradeNo("ORDER_001")
                                .amount(1)
                                .build()));
    }

    /**
     * 验证 HTTP 202 只表示请求已受理，不得将其载荷视为最终交易成功结果。
     */
    @Test
    void acceptedResponseIsNotTreatedAsFinalSuccess() {
        server.enqueueSigned(
                202,
                "{\"code_url\":\"weixin://wxpay/bizpayurl?pr=pending\"}"
        );

        assertThrows(
                WechatPayProtocolException.class,
                () -> client.application(APPID, "https://example.com/pay/notify")
                        .nativePay()
                        .prepay(PrepayRequest.builder()
                                .description("测试")
                                .outTradeNo("ORDER_001")
                                .amount(1)
                                .build())
        );
    }

    /**
     * 验证交易查询响应中的商户号与客户端配置不一致时以安全异常拒绝。
     */
    @Test
    void transactionQueryRejectsResponseForAnotherMerchant() {
        server.enqueueSigned(200, """
                {"appid":"wx1234567890","mchid":"1900000999",
                 "out_trade_no":"ORDER_001","trade_state":"NOTPAY",
                 "trade_state_desc":"未支付"}
                """);

        WechatPaySecurityException failure = assertThrows(
                WechatPaySecurityException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(WechatPaySecurityFailure.MERCHANT_MISMATCH,
                failure.failure());
    }

    /**
     * 验证交易和退款响应的商户订单号、商户退款号必须与本次请求一致。
     */
    @Test
    void transactionAndRefundResponsesMustMatchRequestedBusinessIdentifiers() {
        server.enqueueSigned(200, """
                {"appid":"wx1234567890","mchid":"1900000109",
                 "out_trade_no":"ORDER_002","trade_state":"NOTPAY",
                 "trade_state_desc":"未支付"}
                """);

        WechatPaySecurityException transactionFailure = assertThrows(
                WechatPaySecurityException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(WechatPaySecurityFailure.RESPONSE_MISMATCH,
                transactionFailure.failure());

        server.enqueueSigned(200, refundJson().replace(
                "\"refund\":50", "\"refund\":40"));
        RefundRequest request = RefundRequest.builder()
                .outTradeNo("ORDER_001")
                .outRefundNo("REFUND_001")
                .amount(50, 100)
                .build();

        WechatPaySecurityException refundFailure = assertThrows(
                WechatPaySecurityException.class,
                () -> client.refunds().apply(request));
        assertEquals(WechatPaySecurityFailure.RESPONSE_MISMATCH,
                refundFailure.failure());
    }

    /**
     * 验证关闭微信支付客户端不会关闭由调用方注入的 HTTP 客户端。
     */
    @Test
    void closingWechatClientDoesNotCloseBorrowedHttpClient() {
        client.close();
        try (HttpClient borrowed = HttpClient.builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()) {
            try (WechatPayClient borrowedWechat = clientBuilder()
                    .httpClient(borrowed)
                    .build()) {
                borrowedWechat.close();
            }

            server.enqueueUnsigned(200, "ok".getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=utf-8");
            var response = borrowed.send(HttpRequest.get(
                    server.baseUrl().resolve("borrowed-client-check")).build());
            assertEquals(200, response.statusCode());
            assertEquals("ok", response.bodyString());
        }
    }

    /**
     * 验证请求 Builder 拒绝歧义交易标识、重复商品和溢出分账金额等非法组合。
     */
    @Test
    void requestBuildersRejectAmbiguousIdentifiersAndOverflowingContributions() {
        assertThrows(IllegalArgumentException.class, () -> RefundRequest.builder()
                .outRefundNo("REFUND_001")
                .amount(1, 1)
                .build());
        assertThrows(IllegalArgumentException.class, () -> RefundRequest.builder()
                .transactionId("4200000001")
                .outTradeNo("ORDER_001")
                .outRefundNo("REFUND_001")
                .amount(1, 1)
                .build());
        assertThrows(IllegalArgumentException.class,
                () -> WechatPayClient.builder().apiV3Key("too-short"));
        assertThrows(IllegalArgumentException.class,
                () -> WechatPayClient.builder().apiBaseUrl("http://example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> WechatPayClient.builder().apiBaseUrl("https://example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> WechatPayClient.builder().apiBaseUrl("https://example.com/proxy"));
        try (HttpClient unsafeHttpClient = HttpClient.builder().build()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WechatPayClient.builder().httpClient(unsafeHttpClient)
            );
        }
        assertThrows(IllegalArgumentException.class,
                () -> client.application(APPID, "http://example.com/pay/notify"));
    }

    /**
     * 验证客户端 Builder 构建失败后清除私钥等敏感配置，构建成功后拒绝再次使用。
     */
    @Test
    void clientBuilderReportsMissingConfigurationAndCanBeReused() {
        WechatPayClient.Builder incomplete = WechatPayClient.builder()
                .mchid(MCHID)
                .merchantPrivateKey(merchantKeys.getPrivate())
                .merchantSerialNo("ABCDEF0123456789")
                .apiV3Key(API_V3_KEY)
                .wechatPayPublicKeyId(WechatPayTestServer.PUBLIC_KEY_ID);

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, incomplete::build);
        assertTrue(missing.getMessage().contains("wechatPayPublicKey"));

        WechatPayClient.Builder builder = clientBuilder();
        try (WechatPayClient first = builder.build(); WechatPayClient second = builder.build()) {
            first.close();
            assertThrows(IllegalStateException.class, first::transactions);
            second.transactions();
        }
    }

    /**
     * 验证根客户端关闭后，关闭前取得的交易、退款、账单、通知和 Native 支付入口均拒绝继续工作。
     */
    @Test
    void closedClientRejectsExistingEntryObjects() {
        var transactions = client.transactions();
        var refunds = client.refunds();
        var bills = client.bills();
        var notifications = client.notifications();
        var nativePay = client.application(
                APPID, "https://example.com/pay/notify").nativePay();

        client.close();

        assertThrows(IllegalStateException.class,
                () -> transactions.queryByOutTradeNo("ORDER_001"));
        assertThrows(IllegalStateException.class, () -> refunds.apply(null));
        assertThrows(IllegalStateException.class, () -> bills.applyTradeBill(null));
        assertThrows(IllegalStateException.class,
                () -> notifications.parseTransaction(null, (byte[]) null));
        assertThrows(IllegalStateException.class, () -> nativePay.prepay(null));
    }

    /**
     * 按 APIv3 规则重建请求签名串，并使用商户公钥校验 Authorization 中的签名。
     *
     * @param request 模拟服务端捕获的实际 HTTP 请求
     * @return 签名能否由测试商户公钥验证通过
     */
    private static boolean verifyRequestSignature(
            WechatPayTestServer.CapturedRequest request) {
        String authorization = request.header("Authorization");
        Matcher matcher = SIGNATURE.matcher(authorization);
        assertTrue(matcher.find());
        byte[] signature = Base64.getDecoder().decode(matcher.group(1));
        String message = request.method() + '\n'
                + request.target() + '\n'
                + CLOCK.instant().getEpochSecond() + '\n'
                + REQUEST_NONCE + '\n'
                + new String(request.body(), StandardCharsets.UTF_8) + '\n';
        return CryptoUtils.rsaSha256Verify(merchantKeys.getPublic(),
                message.getBytes(StandardCharsets.UTF_8), signature);
    }

    /**
     * 返回退款申请和查询测试共用的处理中退款 JSON。
     *
     * @return 包含订单标识、退款标识和分单位金额的官方响应示例
     */
    private static String refundJson() {
        return """
                {"refund_id":"5000000001","out_refund_no":"REFUND_001",
                 "transaction_id":"4200000001","out_trade_no":"ORDER_001",
                 "channel":"ORIGINAL","user_received_account":"支付用户零钱",
                 "create_time":"2026-08-29T08:00:00+08:00","status":"PROCESSING",
                 "funds_account":"BASIC","amount":{"total":100,"refund":50,
                 "payer_total":100,"payer_refund":50,"settlement_refund":50,
                 "settlement_total":100,"discount_refund":0,"currency":"CNY"}}
                """;
    }
}
