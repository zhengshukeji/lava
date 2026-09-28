/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.wappay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayMoneyUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayOrderUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayPageRedirectFactory;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayTransport;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import com.zhengshuyun.lava.pay.alipay.order.PayForm;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * 固定绑定异步通知与同步返回地址的手机网站支付入口。
 *
 * <p>适用于手机浏览器中的 H5 页面：已安装支付宝时唤起客户端，否则进入支付宝 H5 收银台。
 * 支付宝当前未提供该接口的 REST V3 路径，本入口与电脑网站支付共用官方 {@code pageExecute}
 * 语义生成 AOP 页面跳转数据。POST 模式返回自动提交表单，GET 模式返回支付 URL；支付宝官方推荐
 * 优先使用 POST。</p>
 */
public final class WapPayClient {
    /** 手机网站支付的固定 AOP 方法名。 */
    private static final String METHOD = "alipay.trade.wap.pay";
    /** 手机网站支付产品码，固定为 {@code QUICK_WAP_WAY}。 */
    private static final String PRODUCT_CODE = "QUICK_WAP_WAY";

    /** 根客户端共享的传输层，用于检查关闭状态和读取协议时间。 */
    private final AlipayTransport transport;
    /** 生成已签名页面支付表单与跳转地址的工厂。 */
    private final AlipayPageRedirectFactory redirects;
    /** 支付结果异步通知地址，随公共参数参与 AOP 签名。 */
    private final URI notifyUrl;
    /** 支付完成后的浏览器同步返回地址，不可作为支付成功依据。 */
    private final URI returnUrl;

    /**
     * 由根客户端创建手机网站支付入口。
     *
     * @param transport 共享协议传输层
     * @param redirects 页面支付跳转工厂
     * @param notifyUrl 异步通知地址
     * @param returnUrl 同步返回地址
     */
    public WapPayClient(AlipayTransport transport, AlipayPageRedirectFactory redirects,
                         URI notifyUrl, URI returnUrl) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.redirects = ValidationUtils.requireNonNull(redirects, "redirects");
        this.notifyUrl = AlipayValidationUtils.requireCallbackUrl(notifyUrl, "notifyUrl");
        this.returnUrl = AlipayValidationUtils.requireCallbackUrl(returnUrl, "returnUrl");
    }

    /**
     * 生成已签名的自动提交 POST 表单。
     *
     * @param request 单笔订单业务参数
     * @return 应作为 HTML 响应正文输出的表单
     */
    public PayForm createForm(WapPayRequest request) {
        // 1. 校验订单时效并转换为支付宝页面支付业务载荷。
        WapPayPayload payload = createPayload(request);

        // 2. 注入公共参数和回调地址，完成 RSA2 签名、HTML 转义与 POST 表单组装。
        return new PayForm(redirects.createForm(
                METHOD,
                payload,
                notifyUrl,
                returnUrl
        ));
    }

    /**
     * 生成已签名的 GET 支付地址。
     *
     * <p>前端可直接打开或重定向到返回地址。该地址包含完整业务参数和签名，不得写入应用日志；
     * 参数较多或地址过长时应改用 {@link #createForm(WapPayRequest)}。</p>
     *
     * @param request 单笔订单业务参数
     * @return 可直接交给浏览器打开的支付宝支付绝对地址
     * @throws IllegalArgumentException 请求为空
     * @throws com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException
     *         GET 地址超过支付宝 {@code pageRedirectionData} 的 16384 字符上限
     * @throws IllegalStateException 根客户端已经关闭
     */
    public URI createUrl(WapPayRequest request) {
        // 1. 校验订单时效并转换为支付宝页面支付业务载荷。
        WapPayPayload payload = createPayload(request);

        // 2. 将全部公共参数、回调地址和业务参数签名后编码进 GET 查询串。
        return redirects.createUrl(
                METHOD,
                payload,
                notifyUrl,
                returnUrl
        );
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
     * 获取当前入口绑定的同步返回地址。
     *
     * @return 固定同步返回地址
     */
    public URI returnUrl() {
        return returnUrl;
    }

    /**
     * 校验手机网站支付请求并转换为固定产品参数的 AOP 业务载荷。
     *
     * @param request 单笔订单业务参数
     * @return 金额、时间、渠道及商品字段均已转换完成的不可变载荷
     * @throws IllegalArgumentException 请求为空
     * @throws IllegalStateException 根客户端已经关闭
     */
    private WapPayPayload createPayload(WapPayRequest request) {
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");

        // 时效、渠道、回传参数和商品明细与电脑网站支付编码规则一致，统一走共享转换
        return new WapPayPayload(
                request.outTradeNo(),
                AlipayMoneyUtils.formatPositive(request.totalAmount()),
                request.subject(),
                PRODUCT_CODE,
                request.body(),
                AlipayOrderUtils.timeExpire(request.timeExpire()),
                AlipayOrderUtils.timeoutExpress(request.timeout()),
                AlipayOrderUtils.goodsDetail(request.goodsDetail()),
                AlipayOrderUtils.channels(request.enablePayChannels()),
                AlipayOrderUtils.channels(request.disablePayChannels()),
                request.quitUrl() == null ? null : request.quitUrl().toASCIIString(),
                request.storeId(),
                request.merchantOrderNo(),
                AlipayOrderUtils.passbackParams(request.passbackParams())
        );
    }

    /**
     * 手机网站支付 {@code biz_content} 的最终协议载荷。
     *
     * @param outTradeNo        商户订单号，最长 64 字符
     * @param totalAmount       订单金额，单位为元、固定保留两位小数
     * @param subject           订单标题，最长 256 字符
     * @param productCode       产品码，固定为 {@code QUICK_WAP_WAY}
     * @param body              可选订单描述，最长 400 字符
     * @param timeExpire        可选绝对过期时间，格式为 {@code yyyy-MM-dd HH:mm:ss}
     * @param timeoutExpress    可选相对有效期，以整分钟文本表示
     * @param goodsDetail       可选商品明细；没有时为 {@code null}
     * @param enablePayChannels 可选允许渠道列表，逗号分隔
     * @param disablePayChannels 可选禁用渠道列表，逗号分隔
     * @param quitUrl           可选用户中途退出返回地址
     * @param storeId           可选商户门店编号，最长 32 字符
     * @param merchantOrderNo   可选商户原始订单号，最长 32 字符
     * @param passbackParams    可选业务回传参数，已完成一次 URL 编码
     */
    private record WapPayPayload(
            @JsonProperty("out_trade_no") String outTradeNo,
            @JsonProperty("total_amount") String totalAmount,
            @JsonProperty("subject") String subject,
            @JsonProperty("product_code") String productCode,
            @JsonProperty("body") @Nullable String body,
            @JsonProperty("time_expire") @Nullable String timeExpire,
            @JsonProperty("timeout_express") @Nullable String timeoutExpress,
            @JsonProperty("goods_detail") @Nullable List<?> goodsDetail,
            @JsonProperty("enable_pay_channels") @Nullable String enablePayChannels,
            @JsonProperty("disable_pay_channels") @Nullable String disablePayChannels,
            @JsonProperty("quit_url") @Nullable String quitUrl,
            @JsonProperty("store_id") @Nullable String storeId,
            @JsonProperty("merchant_order_no") @Nullable String merchantOrderNo,
            @JsonProperty("passback_params") @Nullable String passbackParams
    ) {
    }
}
