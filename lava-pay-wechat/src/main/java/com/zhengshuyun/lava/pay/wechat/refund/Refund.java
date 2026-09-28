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

package com.zhengshuyun.lava.pay.wechat.refund;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityFailure;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 微信支付退款单，供退款申请和查询接口复用。
 *
 * @param refundId 微信支付退款单号
 * @param outRefundNo 商户退款单号
 * @param transactionId 微信支付订单号
 * @param outTradeNo 商户订单号
 * @param channel 退款渠道原始值
 * @param userReceivedAccount 退款入账账户
 * @param successTime 退款成功时间
 * @param createTime 退款创建时间
 * @param status 退款状态原始值
 * @param fundsAccount 退款资金账户原始值
 * @param amount 退款金额明细
 * @param promotionDetail 优惠退款详情
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Refund(
        @JsonProperty("refund_id") String refundId,
        @JsonProperty("out_refund_no") String outRefundNo,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("out_trade_no") String outTradeNo,
        @JsonProperty("channel") String channel,
        @JsonProperty("user_received_account") String userReceivedAccount,
        @JsonProperty("success_time") @Nullable OffsetDateTime successTime,
        @JsonProperty("create_time") OffsetDateTime createTime,
        @JsonProperty("status") String status,
        @JsonProperty("funds_account") String fundsAccount,
        @JsonProperty("amount") @Nullable Amount amount,
        @JsonProperty("promotion_detail") @Nullable List<PromotionDetail> promotionDetail
) {

    /**
     * 复制优惠列表以保持记录不可变。
     */
    public Refund {
        if (promotionDetail != null) {
            promotionDetail = List.copyOf(promotionDetail);
        }
    }

    /**
     * 使用后端可信退款记录核对订单号、退款单号和金额。
     *
     * @param expectedOutTradeNo  可信商户订单号
     * @param expectedOutRefundNo 可信商户退款单号
     * @param expectedTotal       可信原订单金额，单位为分
     * @param expectedRefund      可信退款金额，单位为分
     * @return 当前退款单
     * @throws WechatPaySecurityException 任一关键字段不匹配
     */
    public Refund requireRefund(
            String expectedOutTradeNo,
            String expectedOutRefundNo,
            long expectedTotal,
            long expectedRefund
    ) {
        ValidationUtils.requireNotBlank(
                expectedOutTradeNo,
                "expectedOutTradeNo must not be blank"
        );
        ValidationUtils.requireNotBlank(
                expectedOutRefundNo,
                "expectedOutRefundNo must not be blank"
        );
        WechatPayValidationUtils.requirePositive(expectedTotal, "expectedTotal");
        WechatPayValidationUtils.requirePositive(expectedRefund, "expectedRefund");
        if (!expectedOutTradeNo.equals(outTradeNo)
                || !expectedOutRefundNo.equals(outRefundNo)
                || amount == null
                || expectedTotal != amount.total
                || expectedRefund != amount.refund) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        return this;
    }

    /**
     * 使用后端已保存的微信侧标识完整核对退款单。
     *
     * @param expectedOutTradeNo  可信商户订单号
     * @param expectedTransactionId 可信微信支付订单号
     * @param expectedOutRefundNo 可信商户退款单号
     * @param expectedRefundId    可信微信支付退款单号
     * @param expectedTotal       可信原订单金额，单位为分
     * @param expectedRefund      可信退款金额，单位为分
     * @return 当前退款单
     * @throws WechatPaySecurityException 任一关键字段不匹配
     */
    public Refund requireRefund(
            String expectedOutTradeNo,
            String expectedTransactionId,
            String expectedOutRefundNo,
            String expectedRefundId,
            long expectedTotal,
            long expectedRefund
    ) {
        requireRefund(
                expectedOutTradeNo,
                expectedOutRefundNo,
                expectedTotal,
                expectedRefund
        );
        expectedTransactionId = ValidationUtils.requireNotBlank(
                expectedTransactionId, "expectedTransactionId must not be blank");
        expectedRefundId = ValidationUtils.requireNotBlank(expectedRefundId, "expectedRefundId must not be blank");
        if (!expectedTransactionId.equals(transactionId)
                || !expectedRefundId.equals(refundId)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        return this;
    }

    /**
     * 退款金额明细。
     *
     * @param total 原订单金额
     * @param refund 退款金额
     * @param from 退款出资账户列表
     * @param payerTotal 用户实际支付金额
     * @param payerRefund 用户实际退款金额
     * @param settlementRefund 应结退款金额
     * @param settlementTotal 应结订单金额
     * @param discountRefund 优惠退款金额
     * @param currency 退款币种
     * @param refundFee 手续费退款金额
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            @JsonProperty("total") long total,
            @JsonProperty("refund") long refund,
            @JsonProperty("from") @Nullable List<AmountFrom> from,
            @JsonProperty("payer_total") long payerTotal,
            @JsonProperty("payer_refund") long payerRefund,
            @JsonProperty("settlement_refund") long settlementRefund,
            @JsonProperty("settlement_total") long settlementTotal,
            @JsonProperty("discount_refund") long discountRefund,
            @JsonProperty("currency") String currency,
            @JsonProperty("refund_fee") @Nullable Long refundFee
    ) {
        /**
         * 复制退款出资列表。
         */
        public Amount {
            if (from != null) {
                from = List.copyOf(from);
            }
        }
    }

    /**
     * 退款出资账户。
     *
     * @param account 账户类型原始值
     * @param amount 出资金额
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmountFrom(
            @JsonProperty("account") String account,
            @JsonProperty("amount") long amount) {
    }

    /**
     * 优惠退款详情。
     *
     * @param promotionId 优惠 ID
     * @param scope 优惠范围原始值
     * @param type 优惠类型原始值
     * @param amount 优惠金额
     * @param refundAmount 优惠退款金额
     * @param goodsDetail 优惠商品退款详情
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromotionDetail(
            @JsonProperty("promotion_id") String promotionId,
            @JsonProperty("scope") String scope,
            @JsonProperty("type") String type,
            @JsonProperty("amount") long amount,
            @JsonProperty("refund_amount") long refundAmount,
            @JsonProperty("goods_detail") @Nullable List<GoodsDetail> goodsDetail
    ) {
        /**
         * 复制优惠退款商品列表。
         */
        public PromotionDetail {
            if (goodsDetail != null) {
                goodsDetail = List.copyOf(goodsDetail);
            }
        }
    }

    /**
     * 优惠退款商品详情。
     *
     * @param merchantGoodsId 商户侧商品编码
     * @param wechatpayGoodsId 微信支付商品编码
     * @param goodsName 商品名称
     * @param unitPrice 商品单价
     * @param refundAmount 商品退款金额
     * @param refundQuantity 商品退货数量
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoodsDetail(
            @JsonProperty("merchant_goods_id") String merchantGoodsId,
            @JsonProperty("wechatpay_goods_id") @Nullable String wechatpayGoodsId,
            @JsonProperty("goods_name") @Nullable String goodsName,
            @JsonProperty("unit_price") long unitPrice,
            @JsonProperty("refund_amount") long refundAmount,
            @JsonProperty("refund_quantity") long refundQuantity
    ) {
    }
}
