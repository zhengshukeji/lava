/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.jsapipay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayException;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayMoneyUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayOrderUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayTransport;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 固定绑定异步通知地址的支付宝小程序支付（JSAPI）入口。
 *
 * <p>调用 OpenAPI V3 统一收单交易创建接口生成支付宝交易号，小程序前端再用该交易号唤起收银台。
 * 该入口复用根客户端的签名、HTTP 连接与响应验签能力。</p>
 */
public final class JsapiPayClient {
    /** 统一收单交易创建 OpenAPI V3 接口固定路径。 */
    private static final String CREATE_PATH = "/v3/alipay/trade/create";
    /** 小程序支付产品码，固定为 {@code JSAPI_PAY}。 */
    private static final String PRODUCT_CODE = "JSAPI_PAY";

    /** 根客户端共享的传输层与关闭状态。 */
    private final AlipayTransport transport;
    /** 支付结果异步通知地址，随请求体发送。 */
    private final URI notifyUrl;

    /**
     * 由根客户端创建小程序支付入口。
     *
     * @param transport 共享协议传输层
     * @param notifyUrl 异步通知地址
     */
    public JsapiPayClient(AlipayTransport transport, URI notifyUrl) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.notifyUrl = AlipayValidationUtils.requireCallbackUrl(notifyUrl, "notifyUrl");
    }

    /**
     * 创建小程序支付交易，并核对支付宝返回的商户订单号。
     *
     * @param request 单笔订单业务参数
     * @return 已验签且订单号与请求一致的交易创建结果
     * @throws IllegalArgumentException {@code request} 为 {@code null}
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签或标识核对失败
     */
    public JsapiPayResult create(JsapiPayRequest request) {
        // 1. 注入固定产品码与通知地址，按与页面支付一致的规则转换时效、商品和回传参数。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        CreatePayload payload = new CreatePayload(
                request.outTradeNo(),
                AlipayMoneyUtils.formatPositive(request.totalAmount()),
                request.subject(),
                PRODUCT_CODE,
                request.body(),
                request.buyerOpenId(),
                request.buyerId(),
                request.opAppId(),
                AlipayOrderUtils.timeExpire(request.timeExpire()),
                AlipayOrderUtils.timeoutExpress(request.timeout()),
                AlipayOrderUtils.goodsDetail(request.goodsDetail()),
                request.storeId(),
                AlipayOrderUtils.passbackParams(request.passbackParams()),
                notifyUrl.toASCIIString()
        );

        // 2. 由传输层完成签名、发送和响应验签。
        CreateResponse response = transport.execute(
                CREATE_PATH,
                HttpMethod.POST,
                payload,
                Map.of(),
                CreateResponse.class
        );

        // 3. 交易号是前端唤起收银台的唯一凭据，订单号必须与请求一致，防止把其他订单的响应当作本次结果。
        String tradeNo = AlipayValidationUtils.requireResponseText(response.tradeNo, "trade_no");
        AlipayValidationUtils.requireSame(request.outTradeNo(), response.outTradeNo);
        return new JsapiPayResult(tradeNo, request.outTradeNo());
    }

    /**
     * 获取当前入口绑定的异步通知地址。
     *
     * @return 固定异步通知地址
     */
    public URI notifyUrl() {
        return notifyUrl;
    }

    /**
     * 统一收单交易创建的最终 JSON 载荷。
     *
     * @param outTradeNo     商户订单号
     * @param totalAmount    订单金额，单位为元、固定保留两位小数
     * @param subject        订单标题
     * @param productCode    产品码，固定为 {@code JSAPI_PAY}
     * @param body           可选订单描述
     * @param buyerOpenId    买家 OpenID，与买家用户 ID 二选一
     * @param buyerId        买家用户 ID，与买家 OpenID 二选一
     * @param opAppId        可选小程序应用 ID
     * @param timeExpire     可选绝对过期时间，格式为 {@code yyyy-MM-dd HH:mm:ss}
     * @param timeoutExpress 可选相对有效期，以整分钟文本表示
     * @param goodsDetail    可选商品明细；没有时为 {@code null}
     * @param storeId        可选商户门店编号
     * @param passbackParams 可选业务回传参数，已完成一次 URL 编码
     * @param notifyUrl      异步通知地址
     */
    private record CreatePayload(
            @JsonProperty("out_trade_no") String outTradeNo,
            @JsonProperty("total_amount") String totalAmount,
            @JsonProperty("subject") String subject,
            @JsonProperty("product_code") String productCode,
            @JsonProperty("body") @Nullable String body,
            @JsonProperty("buyer_open_id") @Nullable String buyerOpenId,
            @JsonProperty("buyer_id") @Nullable String buyerId,
            @JsonProperty("op_app_id") @Nullable String opAppId,
            @JsonProperty("time_expire") @Nullable String timeExpire,
            @JsonProperty("timeout_express") @Nullable String timeoutExpress,
            @JsonProperty("goods_detail") @Nullable List<?> goodsDetail,
            @JsonProperty("store_id") @Nullable String storeId,
            @JsonProperty("passback_params") @Nullable String passbackParams,
            @JsonProperty("notify_url") String notifyUrl
    ) {
    }

    /**
     * 已验签的交易创建原始响应载荷。
     *
     * @param tradeNo    支付宝交易号；正常响应必须存在
     * @param outTradeNo 商户订单号；必须与请求一致
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateResponse(
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_trade_no") @Nullable String outTradeNo) {
    }
}
