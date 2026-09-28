/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.refund;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 支付宝退款查询参数。商户订单号与支付宝交易号至少提供一个；同时提供时支付宝优先使用交易号。
 */
public final class RefundQueryRequest {

    /** 商户订单号；与支付宝交易号至少提供一个，同时存在时支付宝优先使用交易号。 */
    private final @Nullable String outTradeNo;
    /** 支付宝交易号；与商户订单号至少提供一个，同时存在时该字段优先。 */
    private final @Nullable String tradeNo;
    /** 原退款请求的商户幂等号；必须与发起退款时的请求号一致。 */
    private final String outRequestNo;
    /** 不可变扩展响应字段列表；默认请求银行卡冲退信息。 */
    private final List<String> queryOptions;

    /**
     * 使用构建期参数创建并校验不可变退款查询请求。
     *
     * @param builder 已收集原交易标识、退款请求号和扩展查询选项的构建器
     * @throws IllegalArgumentException 未提供交易标识，或订单号、退款请求号、查询选项为空白
     */
    private RefundQueryRequest(Builder builder) {
        ValidationUtils.requireTrue(builder.outTradeNo != null || builder.tradeNo != null,
                "at least one of outTradeNo and tradeNo is required");
        outTradeNo = builder.outTradeNo == null ? null
                : AlipayValidationUtils.requireOutTradeNo(builder.outTradeNo);
        tradeNo = builder.tradeNo == null ? null
                : AlipayValidationUtils.requireTradeNo(builder.tradeNo);
        outRequestNo = AlipayValidationUtils.requireOutRequestNo(builder.outRequestNo);
        queryOptions = List.copyOf(builder.queryOptions);
    }

    /**
     * 创建退款查询请求构建器。
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
     * 获取退款请求号。
     *
     * @return 退款请求号
     */
    public String outRequestNo() {
        return outRequestNo;
    }

    /**
     * 获取退款查询选项。
     *
     * @return 不可变查询选项列表
     */
    public List<String> queryOptions() {
        return queryOptions;
    }

    /** 退款查询 fluent 构建器。 */
    public static final class Builder {
        /** 构建期商户订单号；与支付宝交易号至少配置一个。 */
        private @Nullable String outTradeNo;
        /** 构建期支付宝交易号；与商户订单号同时配置时优先使用。 */
        private @Nullable String tradeNo;
        /** 构建期原退款请求号；未配置时构建失败。 */
        private @Nullable String outRequestNo;
        /** 构建期扩展响应字段集合；默认包含银行卡冲退信息并自动去重。 */
        private final Set<String> queryOptions = new LinkedHashSet<>(
                Set.of(RefundQueryOption.DEPOSIT_BACK_INFO));

        /** 创建空退款查询构建器。 */
        private Builder() {
        }

        /**
         * 配置商户订单号并选择按商户订单查询。
         *
         * @param value 商户订单号
         * @return 当前构建器
         */
        public Builder outTradeNo(String value) {
            outTradeNo = value;
            return this;
        }

        /**
         * 配置支付宝交易号并选择按支付宝交易查询。
         *
         * @param value 支付宝交易号
         * @return 当前构建器
         */
        public Builder tradeNo(String value) {
            tradeNo = value;
            return this;
        }

        /**
         * 配置退款请求号。
         *
         * @param value 退款请求号
         * @return 当前构建器
         */
        public Builder outRequestNo(String value) {
            outRequestNo = value;
            return this;
        }

        /**
         * 添加退款查询选项。
         *
         * @param value {@link RefundQueryOption} 中的查询选项
         * @return 当前构建器
         */
        public Builder addQueryOption(String value) {
            queryOptions.add(ValidationUtils.requireNotBlank(value, "queryOption must not be blank"));
            return this;
        }

        /**
         * 校验参数并构建不可变退款查询请求。
         *
         * @return 不可变退款查询请求
         */
        public RefundQueryRequest build() {
            return new RefundQueryRequest(this);
        }
    }
}
