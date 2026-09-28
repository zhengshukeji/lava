/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.wappay;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import com.zhengshuyun.lava.pay.alipay.order.GoodsDetail;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 手机网站支付单笔订单参数。
 *
 * <p>应用 ID、异步通知地址、同步返回地址和产品码由客户端统一注入。
 * 金额单位为分，构建完成后对象不可变。</p>
 */
public final class WapPayRequest {
    /** 商户订单号，在当前商户范围内保持唯一。 */
    private final String outTradeNo;
    /** 订单总金额，单位为分，必须大于零。 */
    private final long totalAmount;
    /** 订单标题。 */
    private final String subject;
    /** 可选订单描述。 */
    private final @Nullable String body;
    /** 可选绝对支付截止时间，按 GMT+8 解释并与相对有效期二选一。 */
    private final @Nullable LocalDateTime timeExpire;
    /** 可选相对支付有效期，必须为 1 分钟至 15 天的整分钟。 */
    private final @Nullable Duration timeout;
    /** 可选用户付款中途退出时返回商户网站的地址。 */
    private final @Nullable URI quitUrl;
    /** 不可变商品明细列表；没有商品明细时为空列表。 */
    private final List<GoodsDetail> goodsDetail;
    /** 仅允许使用的支付渠道集合，与禁用渠道集合互斥。 */
    private final Set<String> enablePayChannels;
    /** 禁止使用的支付渠道集合，与启用渠道集合互斥。 */
    private final Set<String> disablePayChannels;
    /** 可选商户门店编号，最长 32 个字符。 */
    private final @Nullable String storeId;
    /** 可选商户原始订单号，最长 32 个字符。 */
    private final @Nullable String merchantOrderNo;
    /** 可选业务回传参数，最长 512 个字符，发送前进行一次 URL 编码。 */
    private final @Nullable String passbackParams;

    /**
     * 使用构建期参数创建并校验不可变页面支付请求。
     *
     * @param builder 已收集订单、有效期、退出地址、商品和支付渠道参数的构建器
     * @throws IllegalArgumentException 必填字段缺失、字段越界，或有效期与支付渠道等互斥配置冲突
     */
    private WapPayRequest(Builder builder) {
        outTradeNo = AlipayValidationUtils.requireOutTradeNo(builder.outTradeNo);
        totalAmount = AlipayValidationUtils.requirePositiveAmount(
                ValidationUtils.requireNonNull(builder.totalAmount, "totalAmount is required"), "totalAmount");
        subject = ValidationUtils.requireNotBlank(builder.subject, "subject must not be blank");
        body = builder.body;
        ValidationUtils.requireTrue(builder.timeExpire == null || builder.timeout == null,
                "timeExpire and timeout are mutually exclusive");
        timeExpire = builder.timeExpire;
        timeout = builder.timeout;
        if (timeout != null) {
            // 协议以 "90m" 形式传输相对有效期，只能表达整分钟
            ValidationUtils.requireTrue(timeout.isPositive() && timeout.toSeconds() % 60 == 0,
                    "timeout must be a positive number of whole minutes");
        }

        quitUrl = builder.quitUrl;
        goodsDetail = List.copyOf(builder.goodsDetail);
        enablePayChannels = Collections.unmodifiableSet(
                new LinkedHashSet<>(builder.enablePayChannels));
        disablePayChannels = Collections.unmodifiableSet(
                new LinkedHashSet<>(builder.disablePayChannels));
        ValidationUtils.requireTrue(enablePayChannels.isEmpty() || disablePayChannels.isEmpty(),
                "enablePayChannels and disablePayChannels are mutually exclusive");
        storeId = builder.storeId;
        merchantOrderNo = builder.merchantOrderNo;
        passbackParams = builder.passbackParams;
    }

    /**
     * 创建手机网站支付请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取商户订单号。
     *
     * @return 商户订单号
     */
    public String outTradeNo() {
        return outTradeNo;
    }

    /**
     * 获取订单金额。
     *
     * @return 订单金额，单位为分
     */
    public long totalAmount() {
        return totalAmount;
    }

    /**
     * 获取订单标题。
     *
     * @return 订单标题
     */
    public String subject() {
        return subject;
    }

    /**
     * 获取订单描述。
     *
     * @return 订单描述；没有时为 {@code null}
     */
    public @Nullable String body() {
        return body;
    }

    /**
     * 获取按 GMT+8 解释的绝对支付截止时间。
     *
     * @return 绝对支付截止时间；没有时为 {@code null}
     */
    public @Nullable LocalDateTime timeExpire() {
        return timeExpire;
    }

    /**
     * 获取相对支付超时。
     *
     * @return 相对支付超时；没有时为 {@code null}
     */
    public @Nullable Duration timeout() {
        return timeout;
    }

    /**
     * 获取用户中途退出时返回商户网站的地址。
     *
     * @return 退出返回地址；没有时为 {@code null}
     */
    public @Nullable URI quitUrl() {
        return quitUrl;
    }

    /**
     * 获取商品明细。
     *
     * @return 不可变商品明细列表
     */
    public List<GoodsDetail> goodsDetail() {
        return goodsDetail;
    }

    /**
     * 获取指定可用的支付渠道。
     *
     * @return 不可变指定可用支付渠道集合
     */
    public Set<String> enablePayChannels() {
        return enablePayChannels;
    }

    /**
     * 获取禁用的支付渠道。
     *
     * @return 不可变禁用支付渠道集合
     */
    public Set<String> disablePayChannels() {
        return disablePayChannels;
    }

    /**
     * 获取商户门店号。
     *
     * @return 商户门店号；没有时为 {@code null}
     */
    public @Nullable String storeId() {
        return storeId;
    }

    /**
     * 获取商户原始订单号。
     *
     * @return 商户原始订单号；没有时为 {@code null}
     */
    public @Nullable String merchantOrderNo() {
        return merchantOrderNo;
    }

    /**
     * 获取异步通知回传参数。
     *
     * @return 异步通知回传参数；没有时为 {@code null}
     */
    public @Nullable String passbackParams() {
        return passbackParams;
    }

    /**
     * 手机网站支付请求 fluent 构建器。
     */
    public static final class Builder {
        /** 构建期商户订单号；构建前必须配置。 */
        private @Nullable String outTradeNo;
        /** 构建期订单金额，单位为分；构建前必须配置。 */
        private @Nullable Long totalAmount;
        /** 构建期订单标题；构建前必须配置。 */
        private @Nullable String subject;
        /** 构建期可选订单描述。 */
        private @Nullable String body;
        /** 构建期可选绝对过期时间，与相对有效期互斥。 */
        private @Nullable LocalDateTime timeExpire;
        /** 构建期可选相对有效期，与绝对过期时间互斥。 */
        private @Nullable Duration timeout;
        /** 构建期可选退出返回地址。 */
        private @Nullable URI quitUrl;
        /** 按添加顺序保存的构建期商品明细。 */
        private final List<GoodsDetail> goodsDetail = new ArrayList<>();
        /** 构建期允许渠道集合，与禁用渠道集合互斥。 */
        private final Set<String> enablePayChannels = new LinkedHashSet<>();
        /** 构建期禁用渠道集合，与允许渠道集合互斥。 */
        private final Set<String> disablePayChannels = new LinkedHashSet<>();
        /** 构建期可选商户门店编号。 */
        private @Nullable String storeId;
        /** 构建期可选商户原始订单号。 */
        private @Nullable String merchantOrderNo;
        /** 构建期可选业务回传参数，生成表单时统一 URL 编码。 */
        private @Nullable String passbackParams;

        /** 创建空页面支付请求构建器。 */
        private Builder() {
        }

        /**
         * 配置商户订单号。
         *
         * @param value 商户订单号
         * @return 当前构建器
         */
        public Builder outTradeNo(String value) {
            outTradeNo = value;
            return this;
        }

        /**
         * 配置订单金额。
         *
         * @param value 订单金额，单位为分
         * @return 当前构建器
         */
        public Builder totalAmount(long value) {
            totalAmount = value;
            return this;
        }

        /**
         * 配置订单标题。
         *
         * @param value 订单标题
         * @return 当前构建器
         */
        public Builder subject(String value) {
            subject = value;
            return this;
        }

        /**
         * 配置订单描述。
         *
         * @param value 订单描述
         * @return 当前构建器
         */
        public Builder body(String value) {
            body = value;
            return this;
        }

        /**
         * 配置绝对支付截止时间。
         *
         * @param value 绝对支付截止时间，按 GMT+8 解释
         * @return 当前构建器
         */
        public Builder timeExpire(LocalDateTime value) {
            timeExpire = value;
            return this;
        }

        /**
         * 配置相对支付超时。
         *
         * @param value 相对支付超时
         * @return 当前构建器
         */
        public Builder timeout(Duration value) {
            timeout = value;
            return this;
        }

        /**
         * 配置用户付款中途退出时返回商户网站的地址。
         *
         * @param value 退出返回地址
         * @return 当前构建器
         */
        public Builder quitUrl(URI value) {
            quitUrl = ValidationUtils.requireNonNull(value, "quitUrl must not be null");
            return this;
        }

        /**
         * 使用字符串配置用户付款中途退出时返回商户网站的地址。
         *
         * @param value 退出返回地址
         * @return 当前构建器
         * @throws IllegalArgumentException 地址为空白或语法无效
         */
        public Builder quitUrl(String value) {
            ValidationUtils.requireNotBlank(value, "quitUrl must not be blank");
            try {
                return quitUrl(new URI(value));
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("quitUrl is not a valid URI", exception);
            }
        }

        /**
         * 添加商品明细。
         *
         * @param value 商品明细
         * @return 当前构建器
         */
        public Builder addGoodsDetail(GoodsDetail value) {
            goodsDetail.add(ValidationUtils.requireNonNull(value, "goodsDetail"));
            return this;
        }

        /**
         * 添加指定可用支付渠道。
         *
         * @param value 指定可用支付渠道
         * @return 当前构建器
         */
        public Builder addEnablePayChannel(String value) {
            enablePayChannels.add(requireChannel(value));
            return this;
        }

        /**
         * 添加禁用支付渠道。
         *
         * @param value 禁用支付渠道
         * @return 当前构建器
         */
        public Builder addDisablePayChannel(String value) {
            disablePayChannels.add(requireChannel(value));
            return this;
        }

        /**
         * 配置商户门店号。
         *
         * @param value 商户门店号
         * @return 当前构建器
         */
        public Builder storeId(String value) {
            storeId = value;
            return this;
        }

        /**
         * 配置商户原始订单号。
         *
         * @param value 商户原始订单号
         * @return 当前构建器
         */
        public Builder merchantOrderNo(String value) {
            merchantOrderNo = value;
            return this;
        }

        /**
         * 配置异步通知回传参数。
         *
         * @param value 异步通知回传参数；生成表单时会按支付宝要求 URL 编码
         * @return 当前构建器
         */
        public Builder passbackParams(String value) {
            passbackParams = value;
            return this;
        }

        /**
         * 校验参数并构建不可变支付请求。
         *
         * @return 不可变支付请求
         */
        public WapPayRequest build() {
            return new WapPayRequest(this);
        }

        /**
         * 校验单个支付渠道标识，禁止会破坏支付宝逗号分隔编码的字符。
         *
         * @param value 支付宝支付渠道标识，长度为 1 至 64 个字符
         * @return 通过非空白、长度和分隔符校验的原标识
         * @throws IllegalArgumentException 标识为空白或包含逗号
         */
        private static String requireChannel(String value) {
            value = ValidationUtils.requireNotBlank(value, "payChannel must not be blank");
            ValidationUtils.requireTrue(value.indexOf(',') < 0,
                    "payChannel must not contain commas");
            return value;
        }
    }
}
