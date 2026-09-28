/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay;

import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.http.HttpRequest;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.pay.alipay.bill.BillType;
import com.zhengshuyun.lava.pay.alipay.exception.*;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayCryptoUtils;
import com.zhengshuyun.lava.pay.alipay.jsapipay.*;
import com.zhengshuyun.lava.pay.alipay.order.*;
import com.zhengshuyun.lava.pay.alipay.pagepay.*;
import com.zhengshuyun.lava.pay.alipay.refund.*;
import com.zhengshuyun.lava.pay.alipay.transaction.*;
import com.zhengshuyun.lava.pay.alipay.wappay.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证支付宝 OpenAPI V3 客户端的请求签名、响应验签、业务映射和安全失败语义。
 */
class AlipayClientTest {
    /**
     * 测试请求使用的固定支付宝应用 ID。
     */
    private static final String APP_ID = "2026000000000001";
    /**
     * 测试商户的固定支付宝卖家 ID。
     */
    private static final String SELLER_ID = "2088123456789012";
    /**
     * 用于断言请求时间戳和账单日期边界的固定 UTC 时钟。
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T04:00:00Z"), ZoneOffset.UTC);

    /**
     * 测试应用的 RSA 密钥对：私钥用于客户端签名，公钥用于服务端校验请求。
     */
    private static KeyPair appKeys;
    /**
     * 模拟支付宝的 RSA 密钥对：私钥用于服务端签名，公钥用于客户端验签。
     */
    private static KeyPair alipayKeys;

    /**
     * 每个测试独占的本地支付宝协议模拟服务端。
     */
    private AlipayTestServer server;
    /**
     * 指向本地模拟服务端的待测支付宝根客户端。
     */
    private AlipayClient client;

    /**
     * 为全部测试生成一次性 RSA 2048 位应用密钥和支付宝密钥。
     *
     * @throws Exception 当当前 JCA 环境无法创建 RSA 密钥生成器时抛出
     */
    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        appKeys = generator.generateKeyPair();
        alipayKeys = generator.generateKeyPair();
    }

    /**
     * 在每个测试前启动独立模拟服务端，并构建具有固定时钟的待测客户端。
     */
    @BeforeEach
    void setUp() {
        server = AlipayTestServer.start(alipayKeys.getPrivate());
        client = AlipayClient.builder()
                .appId(APP_ID)
                .sellerId(SELLER_ID)
                .appPrivateKey(appKeys.getPrivate())
                .alipayPublicKey(alipayKeys.getPublic())
                .baseUrl(server.baseUrl())
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
     * 验证电脑网站支付表单包含完整 V3 签名参数，正确换算分到元，并转义业务文本以避免 HTML 注入。
     */
    @Test
    void pagePayGeneratesCompleteSignedPostFormAndEscapesHtml() {
        PagePayRequest request = PagePayRequest.builder()
                .outTradeNo("ORDER_001")
                .totalAmount(123)
                .subject("订单 ORDER_001")
                .body("说明\"><script>alert(1)</script>")
                .timeExpire(LocalDateTime.of(
                        2026,
                        8,
                        29,
                        14,
                        0
                ))
                .qrPayMode(PagePayQrMode.CUSTOM_WIDTH)
                .qrcodeWidth(240)
                .addEnablePayChannel("balance")
                .passbackParams("a=b&c=d")
                .addGoodsDetail(GoodsDetail.builder()
                        .goodsId("SKU_001")
                        .goodsName("测试商品")
                        .quantity(2)
                        .price(50)
                        .build())
                .storeId("STORE_001")
                .build();

        PayForm form = client.pagePay(URI.create("https://pay.example.com/alipay/notify"), URI.create("https://pay.example.com/alipay/return"))
                .createForm(request);

        assertEquals(PayForm.CONTENT_TYPE, "text/html;charset=UTF-8");
        assertFalse(form.html().contains("<script>alert(1)</script>"));
        assertTrue(form.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        String action = htmlUnescape(between(form.html(), "action=\"", "\">"));
        String bizContent = htmlUnescape(between(form.html(), "name=\"biz_content\" value=\"", "\">"));
        URI actionUri = URI.create(action);
        assertEquals("/gateway.do", actionUri.getPath());
        Map<String, String> params = queryParams(actionUri);
        String signature = params.remove("sign");
        params.put("biz_content", bizContent);

        assertTrue(AlipayCryptoUtils.verify(AlipayCryptoUtils.signatureContent(params), signature, appKeys.getPublic()));
        assertEquals("alipay.trade.page.pay", params.get("method"));
        assertEquals("2026-08-29 12:00:00", params.get("timestamp"));
        assertEquals("https://pay.example.com/alipay/notify", params.get("notify_url"));
        assertEquals("https://pay.example.com/alipay/return", params.get("return_url"));
        JsonNode body = JsonCodec.defaultCodec().readTree(bizContent);
        assertEquals("1.23", body.get("total_amount").stringValue());
        assertEquals("FAST_INSTANT_TRADE_PAY", body.get("product_code").stringValue());
        assertEquals("PCWEB", body.get("integration_type").stringValue());
        assertEquals("a%3Db%26c%3Dd", body.get("passback_params").stringValue());
        assertEquals("0.50", body.get("goods_detail").get(0).get("price").stringValue());
    }

    /**
     * 验证电脑网站支付 GET 模式返回可直接跳转的完整地址，并对业务参数和回调地址统一签名。
     */
    @Test
    void pagePayGeneratesCompleteSignedGetUrl() {
        PagePayRequest request = PagePayRequest.builder()
                .outTradeNo("ORDER_001")
                .totalAmount(123)
                .subject("订单 ORDER_001")
                .timeout(Duration.ofMinutes(30))
                .qrPayMode(PagePayQrMode.REDIRECT)
                .passbackParams("a=b&c=d")
                .build();

        URI paymentUrl = client.pagePay(
                URI.create("https://pay.example.com/alipay/notify"),
                URI.create("https://pay.example.com/alipay/return")
        ).createUrl(request);

        assertEquals("/gateway.do", paymentUrl.getPath());
        Map<String, String> params = queryParams(paymentUrl);
        String signature = params.remove("sign");
        assertNotNull(signature);
        assertTrue(AlipayCryptoUtils.verify(
                AlipayCryptoUtils.signatureContent(params),
                signature,
                appKeys.getPublic()
        ));
        assertEquals("alipay.trade.page.pay", params.get("method"));
        assertEquals("2026-08-29 12:00:00", params.get("timestamp"));
        assertEquals("https://pay.example.com/alipay/notify", params.get("notify_url"));
        assertEquals("https://pay.example.com/alipay/return", params.get("return_url"));

        JsonNode body = JsonCodec.defaultCodec().readTree(params.get("biz_content"));
        assertEquals("ORDER_001", body.get("out_trade_no").stringValue());
        assertEquals("1.23", body.get("total_amount").stringValue());
        assertEquals("FAST_INSTANT_TRADE_PAY", body.get("product_code").stringValue());
        assertEquals("a%3Db%26c%3Dd", body.get("passback_params").stringValue());
    }

    /**
     * 验证 GET 页面跳转数据超过支付宝长度上限时拒绝返回 URL，并提示调用方改用 POST 表单。
     */
    @Test
    void pagePayRejectsOversizedGetUrl() {
        PagePayRequest.Builder request = PagePayRequest.builder()
                .outTradeNo("ORDER_001")
                .totalAmount(10_000)
                .subject("超长 GET 页面跳转数据");
        for (int index = 0; index < 50; index++) {
            request.addGoodsDetail(GoodsDetail.builder()
                    .goodsId("SKU_" + index)
                    .goodsName("测试商品" + index)
                    .quantity(1)
                    .price(1)
                    .body("商品说明".repeat(50))
                    .build());
        }

        AlipayProtocolException failure = assertThrows(
                AlipayProtocolException.class,
                () -> client.pagePay(
                        URI.create("https://pay.example.com/alipay/notify"),
                        URI.create("https://pay.example.com/alipay/return")
                ).createUrl(request.build())
        );

        assertTrue(failure.getMessage().contains("POST 表单"));
    }

    /**
     * 验证手机网站支付表单使用 wap.pay 方法和 QUICK_WAP_WAY 产品码，携带退出地址且签名可验证。
     */
    @Test
    void wapPayGeneratesSignedFormWithQuitUrl() {
        WapPayRequest request = WapPayRequest.builder()
                .outTradeNo("ORDER_001")
                .totalAmount(123)
                .subject("订单 ORDER_001")
                .timeout(Duration.ofMinutes(30))
                .quitUrl("https://shop.example.com/order/ORDER_001")
                .passbackParams("a=b&c=d")
                .addGoodsDetail(GoodsDetail.builder()
                        .goodsId("SKU_001")
                        .goodsName("测试商品")
                        .quantity(1)
                        .price(123)
                        .build())
                .build();

        PayForm form = client.wapPay("https://pay.example.com/alipay/notify",
                        "https://pay.example.com/alipay/return")
                .createForm(request);

        String action = htmlUnescape(between(form.html(), "action=\"", "\">"));
        String bizContent = htmlUnescape(between(form.html(), "name=\"biz_content\" value=\"", "\">"));
        Map<String, String> params = queryParams(URI.create(action));
        String signature = params.remove("sign");
        params.put("biz_content", bizContent);
        assertTrue(AlipayCryptoUtils.verify(AlipayCryptoUtils.signatureContent(params), signature, appKeys.getPublic()));
        assertEquals("alipay.trade.wap.pay", params.get("method"));
        assertEquals("https://pay.example.com/alipay/return", params.get("return_url"));
        JsonNode body = JsonCodec.defaultCodec().readTree(bizContent);
        assertEquals("QUICK_WAP_WAY", body.get("product_code").stringValue());
        assertEquals("https://shop.example.com/order/ORDER_001", body.get("quit_url").stringValue());
        assertEquals("30m", body.get("timeout_express").stringValue());
        assertEquals("a%3Db%26c%3Dd", body.get("passback_params").stringValue());
        assertEquals("1.23", body.get("goods_detail").get(0).get("price").stringValue());
        assertFalse(body.has("integration_type"));
        assertFalse(body.has("qr_pay_mode"));

        // GET 模式与 POST 表单共用同一套签名参数
        URI paymentUrl = client.wapPay("https://pay.example.com/alipay/notify",
                "https://pay.example.com/alipay/return").createUrl(request);
        assertEquals("alipay.trade.wap.pay", queryParams(paymentUrl).get("method"));
    }

    /**
     * 验证小程序支付调用 V3 交易创建接口，请求体携带 JSAPI_PAY 产品码、买家 OpenID 与通知地址。
     */
    @Test
    void jsapiPayCreatesTradeWithV3SignedRequest() {
        server.enqueueSigned("""
                {"trade_no":"2026000000000000001","out_trade_no":"ORDER_001"}
                """);

        JsapiPayResult result = client.jsapiPay("https://pay.example.com/alipay/notify")
                .create(JsapiPayRequest.builder()
                        .outTradeNo("ORDER_001")
                        .totalAmount(123)
                        .subject("订单 ORDER_001")
                        .buyerOpenId("074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5")
                        .opAppId("2014072300007148")
                        .timeExpire(LocalDateTime.of(2026, 8, 29, 14, 0))
                        .build());

        assertEquals("2026000000000000001", result.tradeNo());
        assertEquals("ORDER_001", result.outTradeNo());
        AlipayTestServer.CapturedRequest captured = server.takeRequest();
        assertSignedRequest(captured, "POST", "/v3/alipay/trade/create");
        JsonNode body = requestBody(captured);
        assertEquals("JSAPI_PAY", body.get("product_code").stringValue());
        assertEquals("1.23", body.get("total_amount").stringValue());
        assertEquals("074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5",
                body.get("buyer_open_id").stringValue());
        assertEquals("2014072300007148", body.get("op_app_id").stringValue());
        assertEquals("2026-08-29 14:00:00", body.get("time_expire").stringValue());
        assertEquals("https://pay.example.com/alipay/notify", body.get("notify_url").stringValue());
        assertFalse(body.has("buyer_id"));
        assertFalse(body.has("goods_detail"));
    }

    /**
     * 验证小程序支付要求买家标识二选一，且拒绝订单号不一致或缺少交易号的响应。
     */
    @Test
    void jsapiPayRequiresExactlyOneBuyerAndMatchingResponse() {
        assertThrows(IllegalArgumentException.class, () -> JsapiPayRequest.builder()
                .outTradeNo("ORDER_001").totalAmount(1).subject("订单").build());
        assertThrows(IllegalArgumentException.class, () -> JsapiPayRequest.builder()
                .outTradeNo("ORDER_001").totalAmount(1).subject("订单")
                .buyerOpenId("open-id").buyerId("2088102146225135").build());

        JsapiPayRequest request = JsapiPayRequest.builder()
                .outTradeNo("ORDER_001").totalAmount(1).subject("订单")
                .buyerId("2088102146225135").build();
        JsapiPayClient jsapiPay = client.jsapiPay(URI.create("https://pay.example.com/alipay/notify"));

        server.enqueueSigned("""
                {"trade_no":"2026000000000000001","out_trade_no":"ORDER_002"}
                """);
        AlipaySecurityException mismatch = assertThrows(AlipaySecurityException.class,
                () -> jsapiPay.create(request));
        assertEquals(AlipaySecurityFailure.RESPONSE_MISMATCH, mismatch.failure());
        assertEquals("2088102146225135", requestBody(server.takeRequest()).get("buyer_id").stringValue());

        server.enqueueSigned("""
                {"out_trade_no":"ORDER_001"}
                """);
        assertThrows(AlipayProtocolException.class, () -> jsapiPay.create(request));
    }

    /**
     * 验证查询和关单请求使用 V3 JSON 路径与签名，并将交易金额、时间和资金渠道正确映射到领域对象。
     */
    @Test
    void queryAndCloseUseV3SignedJsonRequestsAndValidateIdentifiers() {
        server.enqueueSigned(
                """
                               {"trade_no":"2026000000000000001",
                               "out_trade_no":"ORDER_001","trade_status":"TRADE_SUCCESS",
                               "total_amount":"1.23","buyer_pay_amount":"1.00",
                               "send_pay_date":"2026-08-29 12:01:02",
                               "fund_bill_list":[{"fund_channel":"ALIPAYACCOUNT","amount":"1.23"}],
                               "extra":{"url":"https:\\/\\/example.com\\/x"}}
                               """
        );

        Trade trade = client.transactions().query(
                TradeQueryRequest.builder()
                        .outTradeNo("ORDER_001")
                        .addQueryOption(TradeQueryOption.FUND_BILL_LIST)
                        .build());

        assertTrue(trade.paid());
        assertSame(trade, trade.requireOrder("ORDER_001", 123));
        assertEquals(123, trade.totalAmount());
        assertEquals(100L, trade.buyerPayAmount());
        assertEquals(
                LocalDateTime.of(
                        2026,
                        8,
                        29,
                        12,
                        1,
                        2
                ),
                trade.sendPayDate()
        );
        assertEquals(123, trade.fundBills().getFirst().amount());
        assertSignedRequest(
                server.takeRequest(),
                "POST",
                "/v3/alipay/trade/query"
        );

        server.enqueueSigned(
                """
                               {"trade_no":"2026000000000000001",
                               "out_trade_no":"ORDER_001"}
                               """
        );
        TradeCloseResult closed = client.transactions().closeByOutTradeNo("ORDER_001");
        assertEquals("ORDER_001", closed.outTradeNo());
        assertSignedRequest(
                server.takeRequest(),
                "POST",
                "/v3/alipay/trade/close"
        );
    }

    /**
     * 验证退款申请携带稳定的商户退款号，且当申请未明确成功时可通过退款查询确认状态、金额和银行卡冲退信息。
     */
    @Test
    void refundRequiresStableRequestNumberAndUnknownResultNeedsQuery() {
        server.enqueueSigned(
                """
                               {"trade_no":"2026000000000000001",
                               "out_trade_no":"ORDER_001","buyer_logon_id":"159****0000",
                               "fund_change":"N","refund_fee":"0.50"}
                               """
        );
        RefundResult applied = client.refunds().apply(RefundRequest.builder()
                .outTradeNo("ORDER_001")
                .outRequestNo("REFUND_001")
                .refundAmount(50)
                .reason("用户取消")
                .build());

        assertFalse(applied.succeeded());
        assertEquals(50, applied.refundedAmount());
        AlipayTestServer.CapturedRequest applyRequest = server.takeRequest();
        assertSignedRequest(
                applyRequest,
                "POST",
                "/v3/alipay/trade/refund"
        );
        JsonNode applyBody = requestBody(applyRequest);
        assertEquals("REFUND_001", applyBody.get("out_request_no").stringValue());
        assertEquals("0.50", applyBody.get("refund_amount").stringValue());
        assertEquals(RefundQueryOption.DEPOSIT_BACK_INFO, applyBody.get("query_options").get(0).stringValue());

        server.enqueueSigned(
                """
                               {"trade_no":"2026000000000000001",
                               "out_trade_no":"ORDER_001","out_request_no":"REFUND_001",
                               "total_amount":"1.23","refund_amount":"0.50",
                               "refund_status":"REFUND_SUCCESS","gmt_refund_pay":"2026-08-29 12:05:00",
                               "deposit_back_info":{"has_deposit_back":"true","dback_status":"S",
                               "dback_amount":"0.50"}}
                               """
        );
        RefundQueryResult queried = client.refunds().query(RefundQueryRequest.builder()
                .outTradeNo("ORDER_001")
                .outRequestNo("REFUND_001")
                .build());

        assertTrue(queried.succeeded());
        assertSame(queried, queried.requireRefund(
                "ORDER_001",
                "REFUND_001",
                123,
                50
        ));
        assertEquals(50L, queried.refundAmount());
        assertEquals(50L, queried.depositBackInfo().amount());
        assertSignedRequest(
                server.takeRequest(),
                "POST",
                "/v3/alipay/trade/fastpay/refund/query"
        );
    }

    /**
     * 验证日账单查询只返回通过验签的下载地址，请求使用指定账单日期，且调试文本不泄露下载令牌。
     */
    @Test
    void billQueryReturnsOnlyVerifiedDownloadUrl() {
        server.enqueueSigned(
                """
                               {"bill_download_url":"https://bill.example.com/download?id=token"}
                               """
        );
        var result = client.bills().queryDaily(BillType.TRADE, LocalDate.of(2026, 8, 28));

        assertEquals("bill.example.com", result.downloadUrl().getHost());
        assertFalse(result.toString().contains("token"));
        AlipayTestServer.CapturedRequest captured = server.takeRequest();
        assertSignedRequest(
                captured,
                "GET",
                "/v3/alipay/data/dataservice/bill/downloadurl/query"
        );
        assertEquals("2026-08-28", captured.queryParams().get("bill_date"));
    }

    /**
     * 验证已验签的非成功响应被解析为结构化 API 异常，同时保留状态码、错误码、帮助链接和跟踪 ID，但不暴露原始响应内容。
     */
    @Test
    void signedApiErrorsAreStructuredAndRawResponseIsNotExposed() {
        server.enqueueSigned(
                400,
                """
                               {"code":"ACQ.TRADE_NOT_EXIST","message":"secret-response-value",
                               "links":[{"link":"https://example.com/help","rel":"解决方案"}]}
                               """
        );

        AlipayApiException failure = assertThrows(AlipayApiException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(400, failure.statusCode());
        assertTrue(failure.verified());
        assertEquals("ACQ.TRADE_NOT_EXIST", failure.code());
        assertEquals("https://example.com/help", failure.links().getFirst().link());
        assertEquals("trace-id-001", failure.traceId());
        assertFalse(failure.toString().contains("secret-response-value"));
    }

    /**
     * 验证未签名的结构化 API 错误仍保留 HTTP 状态码和错误码，并显式标记响应未经验签。
     */
    @Test
    void unsignedStructuredApiErrorKeepsStatusAndVerificationState() {
        server.enqueueRaw(
                429,
                """
                        {"code":"RATE_LIMIT","message":"调用频率超限"}
                        """
        );

        AlipayApiException failure = assertThrows(
                AlipayApiException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001")
        );

        assertEquals(429, failure.statusCode());
        assertFalse(failure.verified());
        assertEquals("RATE_LIMIT", failure.code());
    }

    /**
     * 验证 HTTP 202 仅表示已受理，不得将其载荷当作最终交易成功结果。
     */
    @Test
    void acceptedResponseIsNotTreatedAsFinalSuccess() {
        server.enqueueSigned(
                202,
                """
                        {"out_trade_no":"ORDER_001","trade_status":"TRADE_SUCCESS",
                         "total_amount":"1.00"}
                        """
        );

        AlipayTransportException failure = assertThrows(
                AlipayTransportException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001")
        );

        assertEquals(202, failure.statusCode());
    }

    /**
     * 验证成功响应缺少签名或被错误密钥签名时关闭失败，并区分缺失签名与无效签名两类安全原因。
     */
    @Test
    void unsignedAndTamperedResponsesFailClosed() {
        server.enqueueRaw(
                200,
                """
                               {"out_trade_no":"ORDER_001","trade_status":"TRADE_SUCCESS",
                               "total_amount":"1.00"}
                               """
        );
        AlipaySecurityException missing = assertThrows(AlipaySecurityException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(AlipaySecurityFailure.MISSING_SIGNATURE, missing.failure());

        String source = "{\"out_trade_no\":\"ORDER_001\",\"trade_status\":\"TRADE_SUCCESS\","
                + "\"total_amount\":\"1.00\"}";
        String wrongSignature = Base64.getEncoder().encodeToString(
                CryptoUtils.rsaSha256Sign(appKeys.getPrivate(), source.getBytes(StandardCharsets.UTF_8)));
        server.enqueueRawSigned(
                200,
                source,
                wrongSignature
        );
        AlipaySecurityException invalid = assertThrows(AlipaySecurityException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(AlipaySecurityFailure.INVALID_SIGNATURE, invalid.failure());
    }

    /**
     * 验证 V3 响应同时出现多个签名请求头时关闭失败，防止不确定的验签头选择。
     */
    @Test
    void duplicateV3SignatureHeadersFailClosed() {
        server.enqueueDuplicateSignature(
                """
                        {"out_trade_no":"ORDER_001","trade_status":"TRADE_SUCCESS",
                         "total_amount":"1.00"}
                        """
        );

        AlipaySecurityException failure = assertThrows(
                AlipaySecurityException.class,
                () -> client.transactions().queryByOutTradeNo("ORDER_001")
        );

        assertEquals(
                AlipaySecurityFailure.DUPLICATE_SIGNATURE_HEADER,
                failure.failure()
        );
    }

    /**
     * 验证响应订单号不匹配、金额精度非法和上游非 JSON 错误均被拒绝，且传输异常不泄露上游原文。
     */
    @Test
    void responseIdentifiersAmountsAndHttpStatusFailClosed() {
        server.enqueueSigned(
                """
                               {"out_trade_no":"ORDER_OTHER",
                               "trade_status":"TRADE_SUCCESS","total_amount":"1.00"}
                               """
        );
        AlipaySecurityException mismatch = assertThrows(AlipaySecurityException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(AlipaySecurityFailure.RESPONSE_MISMATCH, mismatch.failure());

        server.enqueueSigned(
                """
                               {"out_trade_no":"ORDER_001",
                               "trade_status":"TRADE_SUCCESS","total_amount":"1.001"}
                               """
        );
        assertThrows(AlipayProtocolException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));

        server.enqueueRaw(502, "upstream failure");
        AlipayTransportException transport = assertThrows(AlipayTransportException.class, () -> client.transactions().queryByOutTradeNo("ORDER_001"));
        assertEquals(502, transport.statusCode());
        assertFalse(transport.toString().contains("upstream failure"));
    }

    /**
     * 验证关闭支付宝客户端不会关闭由调用方注入的 HTTP 客户端，调用方仍可继续使用该资源发起请求。
     */
    @Test
    void closingAlipayClientDoesNotCloseBorrowedHttpClient() {
        HttpClient borrowed = HttpClient.builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        try {
            AlipayClient borrowedClient = AlipayClient.builder()
                    .appId(APP_ID)
                    .sellerId(SELLER_ID)
                    .appPrivateKey(appKeys.getPrivate())
                    .alipayPublicKey(alipayKeys.getPublic())
                    .baseUrl(server.baseUrl())
                    .httpClient(borrowed)
                    .build();
            borrowedClient.close();

            server.enqueueRaw(200, "{}");
            var response = borrowed.send(HttpRequest.builder(
                    server.baseUrl().toASCIIString(),
                    HttpMethod.GET
            ).build());
            assertEquals(200, response.statusCode());
        } finally {
            borrowed.close();
        }
    }

    /**
     * 验证 Builder 遵守 V3 标识符、支付渠道互斥、退款号和账单日期等边界，拒绝不安全网关配置，并支持原始 Base64 密钥与一次性构建语义。
     */
    @Test
    void buildersFollowV3IdentifierRulesRejectUnsafeInputsAndAcceptRawBase64Keys() {
        IllegalArgumentException invalidNotifyUrl = assertThrows(IllegalArgumentException.class, () -> client.pagePay("invalid uri", "https://pay.example.com/return"));
        assertTrue(invalidNotifyUrl.getMessage().contains("notifyUrl"));

        assertThrows(
                IllegalArgumentException.class,
                () -> PagePayRequest.builder()
                               .outTradeNo("ORDER_001")
                               .totalAmount(1)
                               .subject("test")
                               .addEnablePayChannel("balance")
                               .addDisablePayChannel("creditCard")
                               .build()
        );
        assertDoesNotThrow(
                () -> TradeQueryRequest.builder()
                               .outTradeNo("ORDER_001")
                               .tradeNo("2026000000000000001")
                               .build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RefundRequest.builder()
                               .outTradeNo("ORDER_001")
                               .refundAmount(1)
                               .build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AlipayClient.builder().baseUrl("https://example.com")
        );
        try (HttpClient unsafeHttpClient = HttpClient.builder().build()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> AlipayClient.builder().httpClient(unsafeHttpClient)
            );
        }

        try (AlipayClient rawKeyClient = AlipayClient.builder()
                .appId(APP_ID)
                .sellerId(SELLER_ID)
                .appPrivateKey(Base64.getEncoder().encodeToString(
                        appKeys.getPrivate().getEncoded()))
                .alipayPublicKey(Base64.getEncoder().encodeToString(
                        alipayKeys.getPublic().getEncoded()))
                .build()) {
            assertEquals(APP_ID, rawKeyClient.appId());
        }

        AlipayClient.Builder incompleteBuilder = AlipayClient.builder()
                .appId(APP_ID)
                .appPrivateKey(appKeys.getPrivate())
                .alipayPublicKey(alipayKeys.getPublic());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, incompleteBuilder::build);
        assertTrue(missing.getMessage().contains("sellerId"));

        // 构建器可复用，每次构建得到独立的客户端
        incompleteBuilder.sellerId(SELLER_ID);
        try (AlipayClient first = incompleteBuilder.build(); AlipayClient second = incompleteBuilder.build()) {
            first.close();
            assertThrows(IllegalStateException.class, first::transactions);
            second.transactions();
        }
    }

    /**
     * 验证根客户端关闭后，关闭前取得的交易和退款入口以及后续取得通知入口均拒绝工作。
     */
    @Test
    void closedClientRejectsPreviouslyObtainedEntries() {
        TransactionClient transactions = client.transactions();
        RefundClient refunds = client.refunds();
        client.close();

        assertThrows(IllegalStateException.class, () -> transactions.queryByOutTradeNo("ORDER_001"));
        assertThrows(IllegalStateException.class, () -> refunds.apply(null));
        assertThrows(IllegalStateException.class, client::notifications);
    }

    /**
     * 断言已捕获请求的方法、V3 路径、鉴权参数、请求 ID 和 RSA 签名均符合支付宝协议。
     *
     * @param request 模拟服务端捕获的实际请求
     * @param expectedMethod 期望的 HTTP 方法
     * @param expectedPath 期望的 V3 接口路径，不含查询参数
     */
    private static void assertSignedRequest(
            AlipayTestServer.CapturedRequest request,
            String expectedMethod,
            String expectedPath
    ) {
        assertEquals(expectedMethod, request.method());
        assertTrue(request.target().startsWith(expectedPath));
        String authorization = request.header("Authorization");
        assertNotNull(authorization);
        String prefix = "ALIPAY-SHA256withRSA ";
        assertTrue(authorization.startsWith(prefix));
        String parameters = authorization.substring(prefix.length());
        int signatureStart = parameters.lastIndexOf(",sign=");
        assertTrue(signatureStart > 0);
        String authString = parameters.substring(0, signatureStart);
        String signature = parameters.substring(signatureStart + ",sign=".length());
        assertTrue(authString.startsWith("app_id=" + APP_ID + ",nonce="));
        assertTrue(authString.endsWith(",timestamp=1787976000000"));
        String requestId = request.header("alipay-request-id");
        assertNotNull(requestId);
        assertEquals(32, requestId.length());

        String source = authString + "\n"
                + expectedMethod + "\n"
                + request.target() + "\n"
                + request.bodyText() + "\n";
        assertTrue(AlipayCryptoUtils.verify(source, signature, appKeys.getPublic()));
    }

    /**
     * 将捕获请求的 UTF-8 JSON 正文解析为可断言的树结构。
     *
     * @param request 模拟服务端捕获的实际请求
     * @return 请求正文对应的 JSON 树
     * @throws RuntimeException 当请求正文不是合法 JSON 时由 JSON 编解码器抛出
     */
    private static JsonNode requestBody(AlipayTestServer.CapturedRequest request) {
        return JsonCodec.defaultCodec().readTree(request.bodyText());
    }

    /**
     * 解析 URI 中的 URL 编码查询参数，同名参数保留最后一个值。
     *
     * @param uri 包含原始查询字符串的 URI
     * @return 按出现顺序排列的已解码参数映射
     * @throws NullPointerException 当 URI 没有查询字符串时抛出
     */
    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : uri.getRawQuery().split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    /**
     * 从文本中截取第一个前缀和其后第一个后缀之间的内容。
     *
     * @param value 待截取的完整文本
     * @param prefix 截取起点标记
     * @param suffix 截取终点标记
     * @return 两个标记之间的文本，不包含标记本身
     * @throws AssertionError 当前缀或其后的后缀不存在时抛出
     */
    private static String between(String value, String prefix, String suffix) {
        int start = value.indexOf(prefix);
        assertTrue(start >= 0, "prefix not found: " + prefix);
        start += prefix.length();
        int end = value.indexOf(suffix, start);
        assertTrue(end >= 0, "suffix not found: " + suffix);
        return value.substring(start, end);
    }

    /**
     * 解码支付表单断言涉及的五种 HTML 实体，不作通用 HTML 解码。
     *
     * @param value 从支付表单属性中提取的已转义文本
     * @return 解码引号、单引号、尖括号和与号后的文本
     */
    private static String htmlUnescape(String value) {
        return value.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
