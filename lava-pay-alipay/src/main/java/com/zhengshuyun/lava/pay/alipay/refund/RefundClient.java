/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.refund;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.pay.alipay.internal.*;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 支付宝 OpenAPI V3 统一收单退款申请与退款查询客户端。
 *
 * <p>该客户端复用根客户端的鉴权、HTTP 连接和响应验签能力。退款申请与查询均会核对响应中的
 * 交易标识；请求发送、支付宝业务处理、响应验签或协议字段解析失败时直接抛出异常，不返回
 * 未经验证的部分结果。</p>
 */
public final class RefundClient {
    /** 统一收单交易退款 OpenAPI V3 接口固定路径。 */
    private static final String APPLY_PATH = "/v3/alipay/trade/refund";
    /** 统一收单退款查询 OpenAPI V3 接口固定路径。 */
    private static final String QUERY_PATH = "/v3/alipay/trade/fastpay/refund/query";

    /** 根客户端共享的传输层与关闭状态；当前业务客户端不单独持有 HTTP 资源。 */
    private final AlipayTransport transport;

    /**
     * 使用根客户端共享运行时创建退款业务入口。
     *
     * @param transport 共享协议传输层
     * @throws IllegalArgumentException {@code transport} 为 {@code null}
     */
    public RefundClient(AlipayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 发起全部或部分退款，并校验响应交易标识、资金变化标志和金额明细。
     *
     * <p>网络结果未知时，调用方必须使用原退款请求号和相同金额重试或调用查询接口，不能生成
     * 新请求号。返回结果仅表示已通过协议校验，是否明确退款成功仍应通过
     * {@link RefundResult#succeeded()} 判断。</p>
     *
     * @param request 已完成业务字段校验且包含稳定退款请求号的退款参数
     * @return 已验签退款结果；应通过 {@link RefundResult#succeeded()} 判断是否明确成功
     * @throws IllegalArgumentException {@code request} 为 {@code null}
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签或协议字段解析失败
     */
    public RefundResult apply(RefundRequest request) {
        // 1. 将稳定退款请求号、金额和可选商品明细组装为官方 V3 退款载荷。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        List<GoodsPayload> goods = request.goodsDetail().isEmpty() ? null
                : request.goodsDetail().stream().map(GoodsPayload::from).toList();
        ApplyPayload response = transport.execute(
                APPLY_PATH,
                HttpMethod.POST,
                new ApplyRequestPayload(
                        request.outTradeNo(),
                        request.tradeNo(),
                        AlipayMoneyUtils.formatPositive(request.refundAmount()),
                        request.reason(),
                        request.outRequestNo(),
                        goods,
                        request.queryOptions()
                ),
                Map.of(),
                ApplyPayload.class
        );

        // 2. 响应验签完成后，将支付宝交易号和商户订单号绑定到本次退款请求。
        String tradeNo = AlipayValidationUtils.requireResponseText(
                response.tradeNo, "trade_no");
        String outTradeNo = AlipayValidationUtils.requireResponseText(
                response.outTradeNo, "out_trade_no");
        requireRequestedTrade(
                request.outTradeNo(),
                request.tradeNo(),
                outTradeNo,
                tradeNo
        );
        // 3. 严格解析资金变化标志和金额明细；调用方只能通过 succeeded 判断明确成功。
        return new RefundResult(
                tradeNo,
                outTradeNo,
                response.fundChange,
                AlipayMoneyUtils.parse(response.refundFee, "refund_fee"),
                AlipayMoneyUtils.parseOptional(response.sendBackFee, "send_back_fee"),
                response.buyerOpenId,
                response.buyerLogonId,
                toFundBills(response.fundBills)
        );
    }

    /**
     * 使用原交易标识和退款请求号查询指定退款，并核对响应中的关联标识。
     *
     * @param request 已完成业务字段校验的退款查询参数；退款请求号必须与申请时一致
     * @return 已验签并完成金额、时间和银行卡冲退字段解析的退款查询结果
     * @throws IllegalArgumentException {@code request} 为 {@code null}
     * @throws AlipayException 请求发送、支付宝业务处理、响应验签、标识核对或协议字段解析失败
     */
    public RefundQueryResult query(RefundQueryRequest request) {
        // 1. 使用原交易标识和稳定退款请求号构造 V3 退款查询载荷。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        QueryPayload response = transport.execute(
                QUERY_PATH,
                HttpMethod.POST,
                new QueryRequestPayload(
                        request.outTradeNo(),
                        request.tradeNo(),
                        request.outRequestNo(),
                        request.queryOptions()
                ),
                Map.of(),
                QueryPayload.class
        );

        // 2. 将响应中的订单号和退款请求号与本次查询条件逐项绑定。
        if (response.outTradeNo != null && request.outTradeNo() != null) {
            AlipayValidationUtils.requireSame(request.outTradeNo(), response.outTradeNo);
        }
        if (response.tradeNo != null && request.tradeNo() != null) {
            AlipayValidationUtils.requireSame(request.tradeNo(), response.tradeNo);
        }
        if (response.outRequestNo != null) {
            AlipayValidationUtils.requireSame(request.outRequestNo(), response.outRequestNo);
        }
        // 3. 严格解析退款状态、金额、时间和冲退信息，再映射为公开不可变结果。
        return new RefundQueryResult(
                response.tradeNo,
                response.outTradeNo,
                response.outRequestNo,
                AlipayMoneyUtils.parseOptional(response.totalAmount, "total_amount"),
                AlipayMoneyUtils.parseOptional(response.refundAmount, "refund_amount"),
                response.refundStatus,
                AlipayDateTimeUtils.parseOptional(response.refundTime, "gmt_refund_pay"),
                AlipayMoneyUtils.parseOptional(response.sendBackFee, "send_back_fee"),
                toDepositBackInfo(response.depositBackInfo),
                toFundBills(response.fundBills)
        );
    }

    /**
     * 将退款响应中的商户订单号和支付宝交易号绑定到原请求，防止其他交易响应被业务误用。
     *
     * @param requestedOutTradeNo 请求中的商户订单号；未使用该定位方式时为 {@code null}
     * @param requestedTradeNo    请求中的支付宝交易号；未使用该定位方式时为 {@code null}
     * @param actualOutTradeNo    响应中的商户订单号
     * @param actualTradeNo       响应中的支付宝交易号
     * @throws com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException
     *         任一已请求交易标识与响应不一致
     */
    private static void requireRequestedTrade(
            @Nullable String requestedOutTradeNo,
            @Nullable String requestedTradeNo,
            String actualOutTradeNo,
            String actualTradeNo
    ) {
        if (requestedTradeNo != null) {
            AlipayValidationUtils.requireSame(requestedTradeNo, actualTradeNo);
        }
        if (requestedOutTradeNo != null) {
            AlipayValidationUtils.requireSame(requestedOutTradeNo, actualOutTradeNo);
        }
    }

    /**
     * 将可选退款资金渠道载荷转换为不可变公开模型。
     *
     * @param values 已验签响应中的资金渠道列表；接口未返回该扩展字段时为 {@code null}
     * @return 不可变资金渠道列表；响应未包含该字段时返回空列表
     * @throws com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException
     *         渠道标识缺失或金额不是合法的非负元金额
     */
    private static List<RefundFundBill> toFundBills(
            @Nullable List<FundBillPayload> values) {
        return values == null ? List.of() : values.stream().map(value ->
                new RefundFundBill(
                        value.fundChannel,
                        AlipayMoneyUtils.parse(value.amount, "fund_bill.amount"),
                        AlipayMoneyUtils.parseOptional(value.realAmount, "fund_bill.real_amount"),
                        value.fundType
                )).toList();
    }

    /**
     * 严格转换可选银行卡冲退信息，并拒绝无法解释的布尔标志或金额、时间格式。
     *
     * @param value 已验签响应中的银行卡冲退载荷；未请求或支付宝未返回时为 {@code null}
     * @return 不可变银行卡冲退信息；输入不存在时返回 {@code null}
     * @throws com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException
     *         冲退标志、金额或时间不符合协议格式
     */
    private static @Nullable DepositBackInfo toDepositBackInfo(
            @Nullable DepositBackPayload value) {
        if (value == null) {
            return null;
        }
        return new DepositBackInfo(
                Boolean.parseBoolean(value.hasDepositBack),
                value.status,
                AlipayMoneyUtils.parseOptional(value.amount, "deposit_back_info.dback_amount"),
                AlipayDateTimeUtils.parseOptional(
                        value.bankAckTime, "deposit_back_info.bank_ack_time"),
                AlipayDateTimeUtils.parseOptional(
                        value.estimatedReceiptTime,
                        "deposit_back_info.est_bank_receipt_time")
        );
    }

    /**
     * 退款申请最终 JSON 载荷；交易标识、幂等号及金额均已由公开请求模型完成校验。
     *
     * @param outTradeNo  商户订单号；与支付宝交易号至少存在一个，未选择该定位方式时为 {@code null}
     * @param tradeNo     支付宝交易号；与商户订单号至少存在一个，同时存在时由支付宝优先使用
     * @param refundAmount 本次退款金额，由分转换为正数元字符串
     * @param reason      退款原因；未配置时为 {@code null}
     * @param outRequestNo 商户退款请求号；同一退款的重试和查询必须保持一致
     * @param goodsDetail 退款商品明细；没有商品级明细时为 {@code null} 并从 JSON 中省略
     * @param queryOptions 需要支付宝返回的扩展字段列表；已校验为官方支持值
     */
    private record ApplyRequestPayload(
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("refund_amount") String refundAmount,
            @JsonProperty("refund_reason") @Nullable String reason,
            @JsonProperty("out_request_no") String outRequestNo,
            @JsonProperty("refund_goods_detail") @Nullable List<GoodsPayload> goodsDetail,
            @JsonProperty("query_options") List<String> queryOptions
    ) {
    }

    /**
     * 退款查询最终 JSON 载荷；定位原交易的标识与退款请求号均已完成格式校验。
     *
     * @param outTradeNo  商户订单号；与支付宝交易号至少存在一个，未选择该定位方式时为 {@code null}
     * @param tradeNo     支付宝交易号；与商户订单号至少存在一个，同时存在时由支付宝优先使用
     * @param outRequestNo 原退款申请使用的商户退款请求号
     * @param queryOptions 需要支付宝返回的扩展字段列表；已校验为官方支持值
     */
    private record QueryRequestPayload(
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_request_no") String outRequestNo,
            @JsonProperty("query_options") List<String> queryOptions
    ) {
    }

    /**
     * 单个退款商品的 JSON 载荷；金额在进入载荷前由分转换为元字符串。
     *
     * @param goodsId       商户商品编号，最长 32 个字符
     * @param refundAmount 该商品退款金额，由分转换为正数元字符串
     * @param outItemId     商家小程序商品 ID；未配置时为 {@code null}
     * @param outSkuId      商家小程序 SKU ID；未配置时为 {@code null}
     * @param certificateNos 外部凭证编号列表；没有凭证时为 {@code null} 并从 JSON 中省略
     */
    private record GoodsPayload(
            @JsonProperty("goods_id") String goodsId,
            @JsonProperty("refund_amount") String refundAmount,
            @JsonProperty("out_item_id") @Nullable String outItemId,
            @JsonProperty("out_sku_id") @Nullable String outSkuId,
            @JsonProperty("out_certificate_no_list") @Nullable List<String> certificateNos
    ) {
        /**
         * 将已校验的公开退款商品明细映射为支付宝 JSON 载荷。
         *
         * @param value 不可变退款商品明细
         * @return 金额已转换为元、空凭证列表已转换为 {@code null} 的协议载荷
         */
        private static GoodsPayload from(RefundGoodsDetail value) {
            return new GoodsPayload(
                    value.goodsId(),
                    AlipayMoneyUtils.formatPositive(value.refundAmount()),
                    value.outItemId(),
                    value.outSkuId(),
                    value.outCertificateNos().isEmpty() ? null : value.outCertificateNos()
            );
        }
    }

    /**
     * 已验签的退款申请原始响应载荷；必需字段会在映射公开结果前再次校验。
     *
     * @param tradeNo     支付宝交易号；正常响应必须存在，缺失会判定为协议错误
     * @param outTradeNo  商户订单号；正常响应必须存在，缺失会判定为协议错误
     * @param fundChange  资金是否发生变化的原始标志，明确返回 {@code Y} 才表示本次退款成功
     * @param refundFee   支付宝返回的退款金额元字符串；缺失或格式非法会判定为协议错误
     * @param sendBackFee 本次商户实际退回金额的元字符串；未返回时为 {@code null}
     * @param buyerOpenId 买家 OpenID；未返回时为 {@code null}
     * @param buyerLogonId 脱敏买家登录账号；未返回时为 {@code null}
     * @param fundBills   退款资金渠道明细；未请求或未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApplyPayload(
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("fund_change") @Nullable String fundChange,
            @JsonProperty("refund_fee") @Nullable String refundFee,
            @JsonProperty("send_back_fee") @Nullable String sendBackFee,
            @JsonProperty("buyer_open_id") @Nullable String buyerOpenId,
            @JsonProperty("buyer_logon_id") @Nullable String buyerLogonId,
            @JsonProperty("refund_detail_item_list") @Nullable List<FundBillPayload> fundBills
    ) {
    }

    /**
     * 已验签的退款查询原始响应载荷；可选字段在映射公开结果时保持为空，非法格式则拒绝响应。
     *
     * @param tradeNo        支付宝交易号；未返回时为 {@code null}
     * @param outTradeNo     商户订单号；未返回时为 {@code null}
     * @param outRequestNo   商户退款请求号；返回时必须与查询条件一致
     * @param totalAmount    原交易金额元字符串；未返回时为 {@code null}
     * @param refundAmount   本次退款金额元字符串；未返回时为 {@code null}
     * @param refundStatus   退款处理状态原始值；未返回时为 {@code null}
     * @param refundTime     退款成功时间文本；未请求、未退款成功或未返回时为 {@code null}
     * @param sendBackFee    本次商户实际退回金额元字符串；未返回时为 {@code null}
     * @param depositBackInfo 银行卡冲退明细；未请求或未返回时为 {@code null}
     * @param fundBills      退款资金渠道明细；未请求或未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QueryPayload(
            @JsonProperty("trade_no") @Nullable String tradeNo,
            @JsonProperty("out_trade_no") @Nullable String outTradeNo,
            @JsonProperty("out_request_no") @Nullable String outRequestNo,
            @JsonProperty("total_amount") @Nullable String totalAmount,
            @JsonProperty("refund_amount") @Nullable String refundAmount,
            @JsonProperty("refund_status") @Nullable String refundStatus,
            @JsonProperty("gmt_refund_pay") @Nullable String refundTime,
            @JsonProperty("send_back_fee") @Nullable String sendBackFee,
            @JsonProperty("deposit_back_info") @Nullable DepositBackPayload depositBackInfo,
            @JsonProperty("refund_detail_item_list") @Nullable List<FundBillPayload> fundBills
    ) {
    }

    /**
     * 已验签退款响应中的单个资金渠道原始载荷。
     *
     * @param fundChannel 资金渠道原始标识；渠道明细存在时必须返回
     * @param amount      该渠道退款金额元字符串；渠道明细存在时必须返回
     * @param realAmount  渠道实际退款金额元字符串；未返回时为 {@code null}
     * @param fundType    银行卡资金类型；非银行卡渠道或未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FundBillPayload(
            @JsonProperty("fund_channel") @Nullable String fundChannel,
            @JsonProperty("amount") @Nullable String amount,
            @JsonProperty("real_amount") @Nullable String realAmount,
            @JsonProperty("fund_type") @Nullable String fundType
    ) {
    }

    /**
     * 已验签退款查询响应中的银行卡冲退原始载荷。
     *
     * @param hasDepositBack 是否发生银行卡冲退的布尔文本；缺失按 {@code false} 处理，非法值拒绝响应
     * @param status 冲退状态原始值；未返回时为 {@code null}
     * @param amount 冲退金额元字符串；未返回时为 {@code null}
     * @param bankAckTime 银行响应时间文本；未返回时为 {@code null}
     * @param estimatedReceiptTime 预计银行入账时间文本；未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepositBackPayload(
            @JsonProperty("has_deposit_back") @Nullable String hasDepositBack,
            @JsonProperty("dback_status") @Nullable String status,
            @JsonProperty("dback_amount") @Nullable String amount,
            @JsonProperty("bank_ack_time") @Nullable String bankAckTime,
            @JsonProperty("est_bank_receipt_time") @Nullable String estimatedReceiptTime
    ) {
    }
}
