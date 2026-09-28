/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayException;
import com.zhengshuyun.lava.pay.alipay.internal.*;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 支付宝 OpenAPI V3 统一收单交易查询与关闭客户端。
 *
 * <p>该客户端复用根客户端的签名、HTTP 连接与响应验签能力。所有交易响应都会在映射公开模型前
 * 核对请求标识并严格解析金额、时间等协议字段；请求发送、支付宝业务处理、响应验签或协议解析
 * 失败时直接抛出异常。</p>
 */
public final class TransactionClient {
    /** 统一收单交易查询 OpenAPI V3 接口固定路径。 */
    private static final String QUERY_PATH = "/v3/alipay/trade/query";
    /** 统一收单交易关闭 OpenAPI V3 接口固定路径。 */
    private static final String CLOSE_PATH = "/v3/alipay/trade/close";

    /** 根客户端共享的传输层与关闭状态；当前业务客户端不单独持有 HTTP 资源。 */
    private final AlipayTransport transport;

    /**
     * 使用根客户端共享运行时创建交易查询与关闭入口。
     *
     * @param transport 共享协议传输层
     * @throws IllegalArgumentException {@code transport} 为 {@code null}
     */
    public TransactionClient(AlipayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 按商户订单号查询交易。
     *
     * @param outTradeNo 商户订单号，必须满足支付宝长度和字符约束
     * @return 已验签、已绑定商户订单号并完成协议字段解析的交易状态
     * @throws IllegalArgumentException 商户订单号为空白
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签、标识核对或协议字段解析失败
     */
    public Trade queryByOutTradeNo(String outTradeNo) {
        return query(TradeQueryRequest.builder().outTradeNo(outTradeNo).build());
    }

    /**
     * 按支付宝交易号查询交易。
     *
     * @param tradeNo 支付宝交易号，必须满足支付宝交易号格式
     * @return 已验签、已绑定支付宝交易号并完成协议字段解析的交易状态
     * @throws IllegalArgumentException 支付宝交易号为空白
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签、标识核对或协议字段解析失败
     */
    public Trade queryByTradeNo(String tradeNo) {
        return query(TradeQueryRequest.builder().tradeNo(tradeNo).build());
    }

    /**
     * 使用完整查询参数获取交易状态，并将响应关键标识绑定到本次请求。
     *
     * @param request 已完成交易标识和扩展查询选项校验的查询参数
     * @return 已验签并完成金额、时间及资金渠道字段解析的交易状态
     * @throws IllegalArgumentException {@code request} 为 {@code null}
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签、标识核对或协议字段解析失败
     */
    public Trade query(TradeQueryRequest request) {
        // 1. 将已校验查询条件编码为官方 V3 JSON 请求，并由传输层完成签名、发送和响应验签。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        TradePayload response = transport.execute(
                QUERY_PATH,
                HttpMethod.POST,
                new QueryPayload(request.outTradeNo(), request.tradeNo(),
                        request.queryOptions().isEmpty() ? null : request.queryOptions()),
                Map.of(),
                TradePayload.class
        );

        // 2. 将响应订单标识绑定到本次请求，防止其他订单的已验签响应被业务误用。
        String outTradeNo = AlipayValidationUtils.requireResponseText(
                response.outTradeNo, "out_trade_no");
        String tradeState = AlipayValidationUtils.requireResponseText(
                response.tradeState, "trade_status");
        if (request.tradeNo() != null) {
            AlipayValidationUtils.requireSame(request.tradeNo(), response.tradeNo);
        }
        if (request.outTradeNo() != null) {
            AlipayValidationUtils.requireSame(request.outTradeNo(), outTradeNo);
        }
        // 3. 严格解析金额和时间字段，再映射为公开不可变交易模型。
        List<Trade.FundBill> fundBills = response.fundBills == null ? List.of()
                : response.fundBills.stream().map(TransactionClient::toFundBill).toList();
        return new Trade(
                response.tradeNo,
                outTradeNo,
                tradeState,
                AlipayMoneyUtils.parse(response.totalAmount, "total_amount"),
                response.buyerOpenId,
                response.buyerUserId,
                response.buyerLogonId,
                AlipayDateTimeUtils.parseOptional(response.sendPayDate, "send_pay_date"),
                AlipayMoneyUtils.parseOptional(response.buyerPayAmount, "buyer_pay_amount"),
                AlipayMoneyUtils.parseOptional(response.receiptAmount, "receipt_amount"),
                AlipayMoneyUtils.parseOptional(response.invoiceAmount, "invoice_amount"),
                AlipayMoneyUtils.parseOptional(response.pointAmount, "point_amount"),
                response.storeId,
                fundBills
        );
    }

    /**
     * 按商户订单号关闭待支付交易。
     *
     * @param outTradeNo 商户订单号，必须满足支付宝长度和字符约束
     * @return 已验签且订单标识与请求一致的关闭结果
     * @throws IllegalArgumentException 商户订单号为空白
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签或标识核对失败
     */
    public TradeCloseResult closeByOutTradeNo(String outTradeNo) {
        return close(TradeCloseRequest.builder().outTradeNo(outTradeNo).build());
    }

    /**
     * 按支付宝交易号关闭待支付交易。
     *
     * @param tradeNo 支付宝交易号，必须满足支付宝交易号格式
     * @return 已验签且订单标识与请求一致的关闭结果
     * @throws IllegalArgumentException 支付宝交易号为空白
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签或标识核对失败
     */
    public TradeCloseResult closeByTradeNo(String tradeNo) {
        return close(TradeCloseRequest.builder().tradeNo(tradeNo).build());
    }

    /**
     * 使用完整参数关闭尚未付款的交易，并核对支付宝返回的交易标识。
     *
     * <p>支付宝不允许关闭已付款或已关闭的交易；该类业务拒绝会作为支付宝 API 异常抛出，
     * 不构造看似成功的关闭结果。</p>
     *
     * @param request 已完成交易标识和可选操作员编号校验的关闭参数
     * @return 已验签且已完成响应标识核对的关闭结果
     * @throws IllegalArgumentException {@code request} 为 {@code null}
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签或标识核对失败
     */
    public TradeCloseResult close(TradeCloseRequest request) {
        // 1. 使用请求标识构造 V3 关单载荷，并取得已验签响应。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        ClosePayload response = transport.execute(
                CLOSE_PATH,
                HttpMethod.POST,
                new CloseRequestPayload(request.outTradeNo(), request.tradeNo(),
                        request.operatorId()),
                Map.of(),
                ClosePayload.class
        );
        // 2. 对支付宝实际返回的标识逐项核对，拒绝响应与关单目标不一致。
        if (request.tradeNo() != null && response.tradeNo != null) {
            AlipayValidationUtils.requireSame(request.tradeNo(), response.tradeNo);
        }
        if (request.outTradeNo() != null && response.outTradeNo != null) {
            AlipayValidationUtils.requireSame(request.outTradeNo(), response.outTradeNo);
        }
        // 3. 仅在标识核对完成后向调用方暴露关单结果。
        return new TradeCloseResult(response.tradeNo, response.outTradeNo);
    }

    /**
     * 将已验签的支付资金渠道载荷转换为不可变公开模型。
     *
     * @param value 单个支付资金渠道原始载荷
     * @return 金额已由元转换为分的资金渠道
     * @throws com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException
     *         渠道标识缺失或金额格式非法
     */
    private static Trade.FundBill toFundBill(FundBillPayload value) {
        return new Trade.FundBill(
                value.fundChannel,
                AlipayMoneyUtils.parse(value.amount, "fund_bill_list.amount"),
                AlipayMoneyUtils.parseOptional(value.realAmount, "fund_bill_list.real_amount"));
    }

    /**
     * 交易查询最终 JSON 载荷；交易标识和扩展字段均已由公开请求模型完成校验。
     *
     * @param outTradeNo  商户订单号；与支付宝交易号至少存在一个，未选择该定位方式时为 {@code null}
     * @param tradeNo     支付宝交易号；与商户订单号至少存在一个，同时存在时由支付宝优先使用
     * @param queryOptions 需要支付宝返回的扩展字段列表；没有选项时为 {@code null} 并从 JSON 中省略
     */
    private record QueryPayload(
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("query_options") @Nullable List<String> queryOptions) {
    }

    /**
     * 交易关闭最终 JSON 载荷；交易标识和操作员编号均已由公开请求模型完成校验。
     *
     * @param outTradeNo 商户订单号；与支付宝交易号至少存在一个，未选择该定位方式时为 {@code null}
     * @param tradeNo    支付宝交易号；与商户订单号至少存在一个，同时存在时由支付宝优先使用
     * @param operatorId 执行关单的商家操作员编号；未配置时为 {@code null}
     */
    private record CloseRequestPayload(
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("operator_id") @Nullable String operatorId) {
    }

    /**
     * 已验签的交易查询原始响应载荷；必需字段会在映射公开交易前再次校验。
     *
     * @param tradeNo        支付宝交易号；尚未生成或未返回时为 {@code null}
     * @param outTradeNo     商户订单号；正常响应必须存在，缺失会判定为协议错误
     * @param tradeState     交易状态原始值；正常响应必须存在，缺失会判定为协议错误
     * @param totalAmount    订单总金额元字符串；缺失或格式非法会判定为协议错误
     * @param buyerOpenId    买家 OpenID；未返回时为 {@code null}
     * @param buyerUserId    兼容存量商户的买家用户 ID；未返回时为 {@code null}
     * @param buyerLogonId   脱敏买家登录账号；未返回时为 {@code null}
     * @param sendPayDate    支付时间文本；交易未付款或未返回时为 {@code null}
     * @param buyerPayAmount 买家实付金额元字符串；未返回时为 {@code null}
     * @param receiptAmount  商家实收金额元字符串；未返回时为 {@code null}
     * @param invoiceAmount  可开票金额元字符串；未返回时为 {@code null}
     * @param pointAmount    积分支付金额元字符串；未返回时为 {@code null}
     * @param storeId        商户门店编号；未返回时为 {@code null}
     * @param fundBills      支付资金渠道明细；未请求或未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TradePayload(
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("trade_status") @Nullable String tradeState,
            @JsonProperty("total_amount") @Nullable String totalAmount,
            @JsonProperty("buyer_open_id") @Nullable String buyerOpenId,
            @JsonProperty("buyer_user_id") @Nullable String buyerUserId,
            @JsonProperty("buyer_logon_id") @Nullable String buyerLogonId,
            @JsonProperty("send_pay_date") @Nullable String sendPayDate,
            @JsonProperty("buyer_pay_amount") @Nullable String buyerPayAmount,
            @JsonProperty("receipt_amount") @Nullable String receiptAmount,
            @JsonProperty("invoice_amount") @Nullable String invoiceAmount,
            @JsonProperty("point_amount") @Nullable String pointAmount,
            @JsonProperty("store_id") @Nullable String storeId,
            @JsonProperty("fund_bill_list") @Nullable List<FundBillPayload> fundBills
    ) {
    }

    /**
     * 已验签交易查询响应中的单个支付资金渠道原始载荷。
     *
     * @param fundChannel 资金渠道原始标识；渠道明细存在时必须返回
     * @param amount      该渠道使用金额元字符串；渠道明细存在时必须返回
     * @param realAmount  渠道实际付款金额元字符串；未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FundBillPayload(
            @JsonProperty("fund_channel") @Nullable String fundChannel,
            @JsonProperty("amount") @Nullable String amount,
            @JsonProperty("real_amount") @Nullable String realAmount) {
    }

    /**
     * 已验签的交易关闭原始响应载荷；支付宝可能只返回其中一种交易标识。
     *
     * @param tradeNo    支付宝交易号；未返回时为 {@code null}，返回时必须与请求一致
     * @param outTradeNo 商户订单号；未返回时为 {@code null}，返回时必须与请求一致
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClosePayload(
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_trade_no") @Nullable String outTradeNo) {
    }
}
