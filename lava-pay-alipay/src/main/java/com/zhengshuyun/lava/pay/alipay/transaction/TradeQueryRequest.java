/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.transaction;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 支付宝交易查询参数。商户订单号与支付宝交易号至少提供一个；同时提供时支付宝优先使用交易号。
 */
public final class TradeQueryRequest {

    /** 商户订单号；与支付宝交易号至少提供一个，同时存在时支付宝优先使用交易号。 */
    private final @Nullable String outTradeNo;
    /** 支付宝交易号；与商户订单号至少提供一个，同时存在时该字段优先。 */
    private final @Nullable String tradeNo;
    /** 不可变扩展响应字段列表；默认为空且不包含重复选项。 */
    private final List<String> queryOptions;

    /**
     * 使用构建期参数创建并校验不可变交易查询请求。
     *
     * @param builder 已收集交易标识和扩展查询选项的构建器
     * @throws IllegalArgumentException 未提供交易标识，或订单号、交易号、查询选项为空白
     */
    private TradeQueryRequest(Builder builder) {
        ValidationUtils.requireTrue(builder.outTradeNo != null || builder.tradeNo != null,
                "at least one of outTradeNo and tradeNo is required");
        outTradeNo = builder.outTradeNo == null ? null
                : AlipayValidationUtils.requireOutTradeNo(builder.outTradeNo);
        tradeNo = builder.tradeNo == null ? null
                : AlipayValidationUtils.requireTradeNo(builder.tradeNo);
        queryOptions = List.copyOf(builder.queryOptions);
    }

    /**
     * 创建交易查询请求构建器。
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
     * 获取查询选项。
     *
     * @return 不可变查询选项列表
     */
    public List<String> queryOptions() {
        return queryOptions;
    }

    /** 交易查询 fluent 构建器。 */
    public static final class Builder {
        /** 构建期商户订单号；与支付宝交易号至少配置一个。 */
        private @Nullable String outTradeNo;
        /** 构建期支付宝交易号；与商户订单号同时配置时优先使用。 */
        private @Nullable String tradeNo;
        /** 构建期扩展响应字段集合；默认为空并按添加顺序自动去重。 */
        private final Set<String> queryOptions = new LinkedHashSet<>();

        /** 创建空交易查询构建器。 */
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
         * 添加交易查询选项。
         *
         * @param value {@link TradeQueryOption} 中的查询选项
         * @return 当前构建器
         */
        public Builder addQueryOption(String value) {
            queryOptions.add(ValidationUtils.requireNotBlank(value, "queryOption must not be blank"));
            return this;
        }

        /**
         * 校验参数并构建不可变查询请求。
         *
         * @return 不可变查询请求
         */
        public TradeQueryRequest build() {
            return new TradeQueryRequest(this);
        }
    }
}
