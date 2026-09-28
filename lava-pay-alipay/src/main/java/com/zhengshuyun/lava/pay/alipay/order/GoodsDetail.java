/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.order;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * 下单共用的单个商品明细，电脑网站、手机网站与小程序支付通用。
 */
public final class GoodsDetail {
    /** 商户侧商品编号，最长 64 个字符。 */
    private final String goodsId;
    /** 商品名称，最长 256 个 Unicode 字符。 */
    private final String goodsName;
    /** 商品数量，必须大于零。 */
    private final long quantity;
    /** 商品单价，单位为分，必须大于零。 */
    private final long price;
    /** 可选支付宝商品编号，最长 32 个字符。 */
    private final @Nullable String alipayGoodsId;
    /** 可选商品类目，最长 24 个字符。 */
    private final @Nullable String goodsCategory;
    /** 可选商品类目树，最长 128 个字符。 */
    private final @Nullable String categoriesTree;
    /** 可选商品说明，最长 400 个字符。 */
    private final @Nullable String body;
    /** 可选商品展示地址，必须是最长 400 字符的绝对 HTTP 或 HTTPS URI。 */
    private final @Nullable URI showUrl;

    /**
     * 使用构建期参数创建并校验不可变商品明细。
     *
     * @param builder 已收集必填商品标识、名称、数量、价格及可选扩展字段的构建器
     * @throws IllegalArgumentException 必填字段缺失，数量或价格不是正数，或展示地址不是绝对 HTTP(S) 地址
     */
    private GoodsDetail(Builder builder) {
        goodsId = ValidationUtils.requireNotBlank(builder.goodsId, "goodsId must not be blank");
        goodsName = ValidationUtils.requireNotBlank(builder.goodsName, "goodsName must not be blank");
        quantity = ValidationUtils.requireNonNull(builder.quantity, "quantity is required");
        ValidationUtils.requireTrue(quantity > 0, "quantity must be positive");
        price = AlipayValidationUtils.requirePositiveAmount(
                ValidationUtils.requireNonNull(builder.price, "price is required"), "price");
        alipayGoodsId = builder.alipayGoodsId;
        goodsCategory = builder.goodsCategory;
        categoriesTree = builder.categoriesTree;
        body = builder.body;
        showUrl = builder.showUrl;
        if (showUrl != null) {
            ValidationUtils.requireTrue(showUrl.isAbsolute()
                            && ("http".equalsIgnoreCase(showUrl.getScheme())
                            || "https".equalsIgnoreCase(showUrl.getScheme()))
                            && showUrl.getUserInfo() == null && showUrl.getRawFragment() == null,
                    "showUrl must be an absolute HTTP or HTTPS URI");
        }
    }

    /**
     * 创建商品明细构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取商户商品编号。
     *
     * @return 商户商品编号
     */
    public String goodsId() {
        return goodsId;
    }

    /**
     * 获取商品名称。
     *
     * @return 商品名称
     */
    public String goodsName() {
        return goodsName;
    }

    /**
     * 获取商品数量。
     *
     * @return 商品数量
     */
    public long quantity() {
        return quantity;
    }

    /**
     * 获取商品单价。
     *
     * @return 商品单价，单位为分
     */
    public long price() {
        return price;
    }

    /**
     * 获取支付宝商品编号。
     *
     * @return 支付宝商品编号；没有时为 {@code null}
     */
    public @Nullable String alipayGoodsId() {
        return alipayGoodsId;
    }

    /**
     * 获取商品类目。
     *
     * @return 商品类目；没有时为 {@code null}
     */
    public @Nullable String goodsCategory() {
        return goodsCategory;
    }

    /**
     * 获取商品类目树。
     *
     * @return 商品类目树；没有时为 {@code null}
     */
    public @Nullable String categoriesTree() {
        return categoriesTree;
    }

    /**
     * 获取商品描述。
     *
     * @return 商品描述；没有时为 {@code null}
     */
    public @Nullable String body() {
        return body;
    }

    /**
     * 获取商品展示地址。
     *
     * @return 商品展示地址；没有时为 {@code null}
     */
    public @Nullable URI showUrl() {
        return showUrl;
    }

    /**
     * 商品明细 fluent 构建器。
     */
    public static final class Builder {
        /** 构建期商户商品编号；构建前必须配置。 */
        private @Nullable String goodsId;
        /** 构建期商品名称；构建前必须配置。 */
        private @Nullable String goodsName;
        /** 构建期商品数量；构建前必须配置且大于零。 */
        private @Nullable Long quantity;
        /** 构建期商品单价，单位为分；构建前必须配置。 */
        private @Nullable Long price;
        /** 构建期可选支付宝商品编号。 */
        private @Nullable String alipayGoodsId;
        /** 构建期可选商品类目。 */
        private @Nullable String goodsCategory;
        /** 构建期可选商品类目树。 */
        private @Nullable String categoriesTree;
        /** 构建期可选商品说明。 */
        private @Nullable String body;
        /** 构建期可选商品展示地址。 */
        private @Nullable URI showUrl;

        /** 创建空商品明细构建器。 */
        private Builder() {
        }

        /**
         * 配置商户商品编号。
         *
         * @param value 商户商品编号
         * @return 当前构建器
         */
        public Builder goodsId(String value) {
            goodsId = value;
            return this;
        }

        /**
         * 配置商品名称。
         *
         * @param value 商品名称
         * @return 当前构建器
         */
        public Builder goodsName(String value) {
            goodsName = value;
            return this;
        }

        /**
         * 配置商品数量。
         *
         * @param value 商品数量
         * @return 当前构建器
         */
        public Builder quantity(long value) {
            quantity = value;
            return this;
        }

        /**
         * 配置商品单价。
         *
         * @param value 商品单价，单位为分
         * @return 当前构建器
         */
        public Builder price(long value) {
            price = value;
            return this;
        }

        /**
         * 配置支付宝商品编号。
         *
         * @param value 支付宝商品编号
         * @return 当前构建器
         */
        public Builder alipayGoodsId(String value) {
            alipayGoodsId = value;
            return this;
        }

        /**
         * 配置商品类目。
         *
         * @param value 商品类目
         * @return 当前构建器
         */
        public Builder goodsCategory(String value) {
            goodsCategory = value;
            return this;
        }

        /**
         * 配置以竖线分隔的商品类目树。
         *
         * @param value 以竖线分隔的商品类目树
         * @return 当前构建器
         */
        public Builder categoriesTree(String value) {
            categoriesTree = value;
            return this;
        }

        /**
         * 配置商品描述。
         *
         * @param value 商品描述
         * @return 当前构建器
         */
        public Builder body(String value) {
            body = value;
            return this;
        }

        /**
         * 配置商品展示地址。
         *
         * @param value 商品展示地址
         * @return 当前构建器
         */
        public Builder showUrl(URI value) {
            showUrl = value;
            return this;
        }

        /**
         * 校验参数并构建不可变商品明细。
         *
         * @return 不可变商品明细
         */
        public GoodsDetail build() {
            return new GoodsDetail(this);
        }
    }
}
