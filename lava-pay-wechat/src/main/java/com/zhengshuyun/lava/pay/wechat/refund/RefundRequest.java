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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 普通支付退款申请。微信支付订单号与商户订单号必须且只能配置一个，
 * 金额均以人民币分为单位，构建失败时抛出 {@link IllegalArgumentException}。
 */
public final class RefundRequest {
    /** 微信支付订单号，最多 32 字符；与商户订单号二选一。 */
    private final @Nullable String transactionId;

    /** 商户订单号，长度为 6–32 字符；与微信支付订单号二选一。 */
    private final @Nullable String outTradeNo;

    /** 商户退款单号，必填且在商户号下唯一，最多 64 个允许字符。 */
    private final String outRefundNo;

    /** 退款原因，可选，最多 80 个 UTF-8 字节。 */
    private final @Nullable String reason;

    /** 退款结果通知地址，可选，必须是不超过 256 字符的公网 HTTPS 地址。 */
    private final @Nullable URI notifyUrl;

    /** 退款出资账户，可选，仅支持 {@code AVAILABLE} 或 {@code UNSETTLED}。 */
    private final @Nullable String fundsAccount;

    /** 本次退款与原订单金额信息，币种固定为人民币，单位为分。 */
    private final Amount amount;

    /** 指定商品退款明细；未配置时为 {@code null}，各项退款金额之和不得超过本次退款额。 */
    private final @Nullable List<GoodsDetail> goodsDetail;

    /**
     * 使用构建期参数创建并校验退款申请。
     *
     * @param builder 已收集退款参数的构建器
     * @throws IllegalArgumentException 订单标识未二选一、必填参数缺失、金额越界或明细合计不合法时抛出
     */
    private RefundRequest(Builder builder) {
        boolean hasTransactionId = builder.transactionId != null;
        boolean hasOutTradeNo = builder.outTradeNo != null;
        ValidationUtils.requireTrue(hasTransactionId != hasOutTradeNo,
                "exactly one of transactionId and outTradeNo is required");
        transactionId = builder.transactionId;
        outTradeNo = builder.outTradeNo;
        outRefundNo = WechatPayValidationUtils.requireOutRefundNo(
                ValidationUtils.requireNonNull(builder.outRefundNo,
                        "outRefundNo is required"));
        reason = builder.reason;
        notifyUrl = builder.notifyUrl;
        fundsAccount = builder.fundsAccount;

        long refund = WechatPayValidationUtils.requirePositive(
                ValidationUtils.requireNonNull(builder.refund, "refund amount is required"),
                "refund");
        long total = WechatPayValidationUtils.requirePositive(
                ValidationUtils.requireNonNull(builder.total, "total amount is required"),
                "total");

        List<AmountFrom> from = builder.amountFrom.isEmpty()
                ? null : List.copyOf(builder.amountFrom);
        amount = new Amount(
                refund,
                from,
                total,
                "CNY"
        );
        goodsDetail = builder.goodsDetail.isEmpty()
                ? null : List.copyOf(builder.goodsDetail);
    }

    /**
     * 创建退款请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回微信支付订单号。
     *
     * @return 微信支付订单号；未使用时为 {@code null}
     */
    @JsonProperty("transaction_id")
    public @Nullable String transactionId() {
        return transactionId;
    }

    /**
     * 返回商户订单号。
     *
     * @return 商户订单号；未使用时为 {@code null}
     */
    @JsonProperty("out_trade_no")
    public @Nullable String outTradeNo() {
        return outTradeNo;
    }

    /**
     * 返回商户退款单号。
     *
     * @return 商户退款单号
     */
    @JsonProperty("out_refund_no")
    public String outRefundNo() {
        return outRefundNo;
    }

    /**
     * 返回退款原因。
     *
     * @return 退款原因；未配置时为 {@code null}
     */
    @JsonProperty("reason")
    public @Nullable String reason() {
        return reason;
    }

    /**
     * 返回退款通知地址。
     *
     * @return 退款通知地址；未配置时为 {@code null}
     */
    @JsonProperty("notify_url")
    public @Nullable String notifyUrl() {
        return notifyUrl == null ? null : notifyUrl.toASCIIString();
    }

    /**
     * 返回退款资金来源。
     *
     * @return 退款资金来源；未配置时为 {@code null}
     */
    @JsonProperty("funds_account")
    public @Nullable String fundsAccount() {
        return fundsAccount;
    }

    /**
     * 返回退款金额信息。
     *
     * @return 退款金额信息
     */
    @JsonProperty("amount")
    public Amount amount() {
        return amount;
    }

    /**
     * 返回退款商品列表。
     *
     * @return 退款商品列表；未配置时为 {@code null}
     */
    @JsonProperty("goods_detail")
    public @Nullable List<GoodsDetail> goodsDetail() {
        return goodsDetail;
    }

    /**
     * 退款请求构建器。
     */
    public static final class Builder {
        /** 待使用的微信支付订单号；与商户订单号二选一，初始为 {@code null}。 */
        private @Nullable String transactionId;

        /** 待使用的商户订单号；与微信支付订单号二选一，初始为 {@code null}。 */
        private @Nullable String outTradeNo;

        /** 待提交的商户退款单号；必填，初始为 {@code null}。 */
        private @Nullable String outRefundNo;

        /** 退款原因；可选，最多 80 个 UTF-8 字节。 */
        private @Nullable String reason;

        /** 退款结果通知地址；可选，初始为 {@code null}。 */
        private @Nullable URI notifyUrl;

        /** 退款出资账户；可选，仅接受 {@code AVAILABLE} 或 {@code UNSETTLED}。 */
        private @Nullable String fundsAccount;

        /** 本次退款金额，单位为分；必填且必须大于 0。 */
        private @Nullable Long refund;

        /** 原订单总金额，单位为分；必填、必须大于 0 且不小于退款金额。 */
        private @Nullable Long total;

        /** 退款出资账户明细；默认为空，配置后账户不得重复且金额合计必须等于退款额。 */
        private final List<AmountFrom> amountFrom = new ArrayList<>();

        /** 指定商品退款明细；默认为空，各项退款金额之和不得超过本次退款额。 */
        private final List<GoodsDetail> goodsDetail = new ArrayList<>();

        /** 创建空退款请求构建器。 */
        private Builder() {
        }

        /**
         * 配置微信支付订单号，并与商户订单号保持二选一。
         *
         * @param value 微信支付订单号
         * @return 当前构建器
         */
        public Builder transactionId(String value) {
            transactionId = ValidationUtils.requireNotBlank(value, "transactionId must not be blank");
            return this;
        }

        /**
         * 配置商户订单号，并与微信支付订单号保持二选一。
         *
         * @param value 商户订单号
         * @return 当前构建器
         */
        public Builder outTradeNo(String value) {
            outTradeNo = WechatPayValidationUtils.requireOutTradeNo(value);
            return this;
        }

        /**
         * 配置商户退款单号。
         *
         * @param value 商户退款单号
         * @return 当前构建器
         */
        public Builder outRefundNo(String value) {
            outRefundNo = WechatPayValidationUtils.requireOutRefundNo(value);
            return this;
        }

        /**
         * 配置退款原因。
         *
         * @param value 退款原因，最多 80 个 UTF-8 字节
         * @return 当前构建器
         */
        public Builder reason(String value) {
            reason = value;
            return this;
        }

        /**
         * 配置退款结果通知地址。
         *
         * @param value 退款结果通知地址
         * @return 当前构建器
         */
        public Builder notifyUrl(URI value) {
            notifyUrl = WechatPayValidationUtils.requireNotifyUrl(value);
            return this;
        }

        /**
         * 使用字符串配置退款结果通知地址。
         *
         * @param value 退款结果通知地址
         * @return 当前构建器
         */
        public Builder notifyUrl(String value) {
            notifyUrl = WechatPayValidationUtils.requireNotifyUrl(value);
            return this;
        }

        /**
         * 配置退款资金来源。
         *
         * @param value 仅支持 {@link RefundFundsAccount#AVAILABLE} 或
         *              {@link RefundFundsAccount#UNSETTLED}
         * @return 当前构建器
         */
        public Builder fundsAccount(String value) {
            fundsAccount = ValidationUtils.requireNotBlank(value, "fundsAccount must not be blank");
            return this;
        }

        /**
         * 配置退款金额与原订单总金额，单位均为分。
         *
         * @param refundValue 本次退款金额
         * @param totalValue 原订单金额
         * @return 当前构建器
         */
        public Builder amount(long refundValue, long totalValue) {
            refund = WechatPayValidationUtils.requirePositive(refundValue, "refund");
            total = WechatPayValidationUtils.requirePositive(totalValue, "total");
            return this;
        }

        /**
         * 追加退款出资账户。
         *
         * @param value 待追加退款出资账户
         * @return 当前构建器
         */
        public Builder addAmountFrom(AmountFrom value) {
            amountFrom.add(ValidationUtils.requireNonNull(value,
                    "amountFrom must not be null"));
            return this;
        }

        /**
         * 追加退款商品。
         *
         * @param value 待追加退款商品
         * @return 当前构建器
         */
        public Builder addGoodsDetail(GoodsDetail value) {
            goodsDetail.add(ValidationUtils.requireNonNull(value,
                    "goodsDetail must not be null"));
            return this;
        }

        /**
         * 校验并创建退款请求。
         *
         * @return 不可变退款请求
         * @throws IllegalArgumentException 订单标识、必填参数、金额或明细不符合退款协议时抛出
         */
        public RefundRequest build() {
            return new RefundRequest(this);
        }
    }

    /**
     * 退款金额信息。
     *
     * @param refund 本次退款金额，单位为分，必须大于 0 且不超过原订单金额
     * @param from 退款出资账户明细；未指定时为 {@code null}，指定时金额合计必须等于退款额
     * @param total 原订单总金额，单位为分，必须大于 0
     * @param currency 币种代码，固定为 {@code CNY}
     */
    public record Amount(
            @JsonProperty("refund") long refund,
            @JsonProperty("from") @Nullable List<AmountFrom> from,
            @JsonProperty("total") long total,
            @JsonProperty("currency") String currency
    ) {
        /**
         * 复制出资明细以保持记录不可变。
         */
        public Amount {
            if (from != null) {
                from = List.copyOf(from);
            }
        }
    }

    /**
     * 退款出资账户及金额。
     *
     * @param account 出资账户，如 {@code AVAILABLE}、{@code UNAVAILABLE}
     * @param amount 出资金额，单位为分
     */
    public record AmountFrom(
            @JsonProperty("account") String account,
            @JsonProperty("amount") long amount) {
        /**
         * 校验出资账户非空白、金额为正数。
         *
         * @throws IllegalArgumentException 账户为空白或金额不大于 0
         */
        public AmountFrom {
            ValidationUtils.requireNotBlank(account, "amountFrom.account must not be blank");
            WechatPayValidationUtils.requirePositive(amount, "amountFrom.amount");
        }
    }

    /**
     * 指定商品退款信息。
     *
     * @param merchantGoodsId 商户侧商品编码
     * @param wechatpayGoodsId 微信支付商品编码
     * @param goodsName 商品名称
     * @param unitPrice 商品单价，单位为分，必须大于 0
     * @param refundAmount 该商品退款金额，单位为分，必须大于 0
     * @param refundQuantity 商品退货数量
     */
    public record GoodsDetail(
            @JsonProperty("merchant_goods_id") String merchantGoodsId,
            @JsonProperty("wechatpay_goods_id") @Nullable String wechatpayGoodsId,
            @JsonProperty("goods_name") @Nullable String goodsName,
            @JsonProperty("unit_price") long unitPrice,
            @JsonProperty("refund_amount") long refundAmount,
            @JsonProperty("refund_quantity") long refundQuantity
    ) {

        /**
         * 校验商品编码非空白，单价、退款额与退货数量为正数。
         *
         * @throws IllegalArgumentException 编码为空白，或数值不大于 0
         */
        public GoodsDetail {
            WechatPayValidationUtils.requireMerchantGoodsId(merchantGoodsId);
            WechatPayValidationUtils.requirePositive(unitPrice, "unitPrice");
            WechatPayValidationUtils.requirePositive(refundAmount, "refundAmount");
            WechatPayValidationUtils.requirePositive(refundQuantity, "refundQuantity");
        }
    }
}
