/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.jsapipay;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import com.zhengshuyun.lava.pay.alipay.order.GoodsDetail;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 小程序支付（JSAPI）单笔订单参数。
 *
 * <p>异步通知地址和产品码由客户端统一注入。买家标识 {@code buyerOpenId} 与 {@code buyerId}
 * 必须且只能配置一个：新商户使用 OpenID，存量按 UID 配置的应用仍可使用 {@code buyerId}。
 * 金额单位为分，构建完成后对象不可变。</p>
 */
public final class JsapiPayRequest {
    /** 商户订单号，在当前商户范围内保持唯一。 */
    private final String outTradeNo;
    /** 订单总金额，单位为分，必须大于零。 */
    private final long totalAmount;
    /** 订单标题。 */
    private final String subject;
    /** 可选订单描述。 */
    private final @Nullable String body;
    /** 买家支付宝 OpenID，与 {@link #buyerId} 二选一。 */
    private final @Nullable String buyerOpenId;
    /** 买家支付宝用户 ID（2088 开头），与 {@link #buyerOpenId} 二选一。 */
    private final @Nullable String buyerId;
    /** 可选小程序应用 ID，小程序与当前 OpenAPI 应用不是同一个时填写。 */
    private final @Nullable String opAppId;
    /** 可选绝对支付截止时间，按 GMT+8 解释并与相对有效期二选一。 */
    private final @Nullable LocalDateTime timeExpire;
    /** 可选相对支付有效期，必须为整分钟。 */
    private final @Nullable Duration timeout;
    /** 不可变商品明细列表；没有商品明细时为空列表。 */
    private final List<GoodsDetail> goodsDetail;
    /** 可选商户门店编号。 */
    private final @Nullable String storeId;
    /** 可选业务回传参数，会在支付结果异步通知中原样返回。 */
    private final @Nullable String passbackParams;

    /**
     * 使用构建期参数创建并校验不可变小程序支付请求。
     *
     * @param builder 已收集订单、买家、有效期和商品参数的构建器
     * @throws IllegalArgumentException 必填字段缺失、买家标识未配置或同时配置、有效期配置冲突
     */
    private JsapiPayRequest(Builder builder) {
        outTradeNo = AlipayValidationUtils.requireOutTradeNo(builder.outTradeNo);
        totalAmount = AlipayValidationUtils.requirePositiveAmount(
                ValidationUtils.requireNonNull(builder.totalAmount, "totalAmount is required"), "totalAmount");
        subject = ValidationUtils.requireNotBlank(builder.subject, "subject must not be blank");
        body = builder.body;
        // 买家标识决定由谁付款，二者都缺或同时存在都会让支付宝无法确定付款人
        ValidationUtils.requireTrue((builder.buyerOpenId == null) != (builder.buyerId == null),
                "exactly one of buyerOpenId and buyerId is required");
        buyerOpenId = builder.buyerOpenId;
        buyerId = builder.buyerId;
        opAppId = builder.opAppId;
        ValidationUtils.requireTrue(builder.timeExpire == null || builder.timeout == null,
                "timeExpire and timeout are mutually exclusive");
        timeExpire = builder.timeExpire;
        timeout = builder.timeout;
        if (timeout != null) {
            // 协议以 "90m" 形式传输相对有效期，只能表达整分钟
            ValidationUtils.requireTrue(timeout.isPositive() && timeout.toSeconds() % 60 == 0,
                    "timeout must be a positive number of whole minutes");
        }
        goodsDetail = List.copyOf(builder.goodsDetail);
        storeId = builder.storeId;
        passbackParams = builder.passbackParams;
    }

    /**
     * 创建小程序支付请求构建器。
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
     * 获取买家支付宝 OpenID。
     *
     * @return 买家 OpenID；按用户 ID 下单时为 {@code null}
     */
    public @Nullable String buyerOpenId() {
        return buyerOpenId;
    }

    /**
     * 获取买家支付宝用户 ID。
     *
     * @return 买家用户 ID；按 OpenID 下单时为 {@code null}
     */
    public @Nullable String buyerId() {
        return buyerId;
    }

    /**
     * 获取小程序应用 ID。
     *
     * @return 小程序应用 ID；没有时为 {@code null}
     */
    public @Nullable String opAppId() {
        return opAppId;
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
     * 获取商品明细。
     *
     * @return 不可变商品明细列表
     */
    public List<GoodsDetail> goodsDetail() {
        return goodsDetail;
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
     * 获取异步通知回传参数。
     *
     * @return 异步通知回传参数；没有时为 {@code null}
     */
    public @Nullable String passbackParams() {
        return passbackParams;
    }

    /**
     * 小程序支付请求 fluent 构建器。
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
        /** 构建期买家 OpenID，与买家用户 ID 二选一。 */
        private @Nullable String buyerOpenId;
        /** 构建期买家用户 ID，与买家 OpenID 二选一。 */
        private @Nullable String buyerId;
        /** 构建期可选小程序应用 ID。 */
        private @Nullable String opAppId;
        /** 构建期可选绝对过期时间，与相对有效期互斥。 */
        private @Nullable LocalDateTime timeExpire;
        /** 构建期可选相对有效期，与绝对过期时间互斥。 */
        private @Nullable Duration timeout;
        /** 按添加顺序保存的构建期商品明细。 */
        private final List<GoodsDetail> goodsDetail = new ArrayList<>();
        /** 构建期可选商户门店编号。 */
        private @Nullable String storeId;
        /** 构建期可选业务回传参数，发送时统一 URL 编码。 */
        private @Nullable String passbackParams;

        /** 创建空小程序支付请求构建器。 */
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
         * 配置买家支付宝 OpenID。
         *
         * @param value 买家 OpenID
         * @return 当前构建器
         */
        public Builder buyerOpenId(String value) {
            buyerOpenId = ValidationUtils.requireNotBlank(value, "buyerOpenId must not be blank");
            return this;
        }

        /**
         * 配置买家支付宝用户 ID，仅适用于按 UID 配置的存量应用。
         *
         * @param value 2088 开头的买家用户 ID
         * @return 当前构建器
         */
        public Builder buyerId(String value) {
            buyerId = ValidationUtils.requireNotBlank(value, "buyerId must not be blank");
            return this;
        }

        /**
         * 配置小程序应用 ID。
         *
         * @param value 商户实际经营主体的小程序应用 ID
         * @return 当前构建器
         */
        public Builder opAppId(String value) {
            opAppId = value;
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
         * 配置异步通知回传参数。
         *
         * @param value 异步通知回传参数；发送时会按支付宝要求 URL 编码
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
        public JsapiPayRequest build() {
            return new JsapiPayRequest(this);
        }
    }
}
