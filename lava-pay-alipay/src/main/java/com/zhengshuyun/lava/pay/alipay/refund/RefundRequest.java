/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.refund;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 支付宝统一收单退款参数。
 *
 * <p>每次退款都强制提供稳定的退款请求号。网络异常后必须使用相同请求号和金额重试或查询，
 * 不得生成新请求号，否则可能重复退款。商户订单号与支付宝交易号至少提供一个；同时提供时支付宝
 * 优先使用交易号。</p>
 */
public final class RefundRequest {

    /** 商户订单号；与支付宝交易号至少提供一个，同时存在时支付宝优先使用交易号。 */
    private final @Nullable String outTradeNo;
    /** 支付宝交易号；与商户订单号至少提供一个，同时存在时该字段优先。 */
    private final @Nullable String tradeNo;
    /** 本次退款金额，单位为分，取值范围为 1 至单笔支付金额上限。 */
    private final long refundAmount;
    /** 商户退款请求号；同一笔退款重试或查询时必须保持不变，用于支付宝幂等控制。 */
    private final String outRequestNo;
    /** 退款原因；可选，最长 256 个字符。 */
    private final @Nullable String reason;
    /** 不可变退款商品明细；各明细退款金额之和不得超过本次退款金额。 */
    private final List<RefundGoodsDetail> goodsDetail;
    /** 不可变扩展响应字段列表；默认请求银行卡冲退信息。 */
    private final List<String> queryOptions;

    /**
     * 使用构建期参数创建并校验不可变退款请求。
     *
     * @param builder 已收集交易标识、退款金额、幂等请求号和可选退款明细的构建器
     * @throws IllegalArgumentException 必填字段缺失、金额不是正数，或商品明细退款金额合计超过本次退款金额
     */
    private RefundRequest(Builder builder) {
        ValidationUtils.requireTrue(builder.outTradeNo != null || builder.tradeNo != null,
                "at least one of outTradeNo and tradeNo is required");
        outTradeNo = builder.outTradeNo == null ? null
                : AlipayValidationUtils.requireOutTradeNo(builder.outTradeNo);
        tradeNo = builder.tradeNo == null ? null
                : AlipayValidationUtils.requireTradeNo(builder.tradeNo);
        refundAmount = AlipayValidationUtils.requirePositiveAmount(ValidationUtils.requireNonNull(builder.refundAmount,
                        "refundAmount is required"), "refundAmount");
        outRequestNo = AlipayValidationUtils.requireOutRequestNo(builder.outRequestNo);
        reason = builder.reason;
        goodsDetail = List.copyOf(builder.goodsDetail);
        long detailAmount = 0;
        for (RefundGoodsDetail item : goodsDetail) {
            ValidationUtils.requireTrue(item.refundAmount() <= refundAmount - detailAmount,
                    "sum of goods refund amounts must not exceed refundAmount");
            detailAmount += item.refundAmount();
        }
        queryOptions = List.copyOf(builder.queryOptions);
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
     * 获取商户订单号。
     *
     * @return 商户订单号；未使用时为 {@code null}
     */
    public @Nullable String outTradeNo() {
        return outTradeNo;
    }

    /**
     * 获取支付宝交易号。
     *
     * @return 支付宝交易号；未使用时为 {@code null}
     */
    public @Nullable String tradeNo() {
        return tradeNo;
    }

    /**
     * 获取本次退款金额。
     *
     * @return 本次退款金额，单位为分
     */
    public long refundAmount() {
        return refundAmount;
    }

    /**
     * 获取退款幂等请求号。
     *
     * @return 退款幂等请求号
     */
    public String outRequestNo() {
        return outRequestNo;
    }

    /**
     * 获取退款原因。
     *
     * @return 退款原因；没有时为 {@code null}
     */
    public @Nullable String reason() {
        return reason;
    }

    /**
     * 获取退款商品明细。
     *
     * @return 不可变退款商品列表
     */
    public List<RefundGoodsDetail> goodsDetail() {
        return goodsDetail;
    }

    /**
     * 获取退款接口返回字段查询选项。
     *
     * @return 不可变查询选项列表
     */
    public List<String> queryOptions() {
        return queryOptions;
    }

    /** 退款请求 fluent 构建器。 */
    public static final class Builder {
        /** 构建期商户订单号；与支付宝交易号至少配置一个。 */
        private @Nullable String outTradeNo;
        /** 构建期支付宝交易号；与商户订单号同时配置时优先使用。 */
        private @Nullable String tradeNo;
        /** 构建期退款金额，单位为分；未配置时构建失败。 */
        private @Nullable Long refundAmount;
        /** 构建期退款幂等请求号；未配置时构建失败。 */
        private @Nullable String outRequestNo;
        /** 构建期退款原因；可选，最长 256 个字符。 */
        private @Nullable String reason;
        /** 构建期退款商品明细；默认为空，构建后复制为不可变列表。 */
        private final List<RefundGoodsDetail> goodsDetail = new ArrayList<>();
        /** 构建期扩展响应字段集合；默认包含银行卡冲退信息并自动去重。 */
        private final Set<String> queryOptions = new LinkedHashSet<>(
                Set.of(RefundQueryOption.DEPOSIT_BACK_INFO));

        /** 创建空退款请求构建器。 */
        private Builder() {
        }

        /**
         * 配置商户订单号并选择按商户订单退款。
         *
         * @param value 商户订单号
         * @return 当前构建器
         */
        public Builder outTradeNo(String value) {
            outTradeNo = value;
            return this;
        }

        /**
         * 配置支付宝交易号并选择按支付宝交易退款。
         *
         * @param value 支付宝交易号
         * @return 当前构建器
         */
        public Builder tradeNo(String value) {
            tradeNo = value;
            return this;
        }

        /**
         * 配置本次退款金额。
         *
         * @param value 本次退款金额，单位为分
         * @return 当前构建器
         */
        public Builder refundAmount(long value) {
            refundAmount = value;
            return this;
        }

        /**
         * 配置退款幂等请求号。
         *
         * @param value 稳定且唯一的退款请求号
         * @return 当前构建器
         */
        public Builder outRequestNo(String value) {
            outRequestNo = value;
            return this;
        }

        /**
         * 配置退款原因。
         *
         * @param value 退款原因
         * @return 当前构建器
         */
        public Builder reason(String value) {
            reason = value;
            return this;
        }

        /**
         * 添加退款商品明细。
         *
         * @param value 退款商品明细
         * @return 当前构建器
         */
        public Builder addGoodsDetail(RefundGoodsDetail value) {
            goodsDetail.add(ValidationUtils.requireNonNull(value, "goodsDetail"));
            return this;
        }

        /**
         * 添加退款接口返回字段查询选项。
         *
         * @param value {@link RefundQueryOption} 中的查询选项
         * @return 当前构建器
         */
        public Builder addQueryOption(String value) {
            queryOptions.add(ValidationUtils.requireNotBlank(value, "queryOption must not be blank"));
            return this;
        }

        /**
         * 校验参数并构建不可变退款请求。
         *
         * @return 不可变退款请求
         */
        public RefundRequest build() {
            return new RefundRequest(this);
        }
    }
}
