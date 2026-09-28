/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.refund;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个退款商品明细。
 */
public final class RefundGoodsDetail {
    /** 商户商品编号；必填，最长 32 个字符。 */
    private final String goodsId;
    /** 该商品的退款金额，单位为分，取值范围为 1 至 999999999。 */
    private final long refundAmount;
    /** 商家小程序商品 ID；可选，最长 64 个字符。 */
    private final @Nullable String outItemId;
    /** 商家小程序 SKU ID；可选，最长 64 个字符。 */
    private final @Nullable String outSkuId;
    /** 不可变外部凭证编号列表；每个编号最长 128 个字符。 */
    private final List<String> outCertificateNos;

    /**
     * 使用构建期参数创建并校验不可变退款商品明细。
     *
     * @param builder 已收集商品编号、退款金额和可选外部商品信息的构建器
     * @throws IllegalArgumentException 商品编号或退款金额缺失，或退款金额不是正数
     */
    private RefundGoodsDetail(Builder builder) {
        goodsId = ValidationUtils.requireNotBlank(builder.goodsId, "goodsId must not be blank");
        refundAmount = AlipayValidationUtils.requirePositiveAmount(ValidationUtils.requireNonNull(builder.refundAmount,
                        "refundAmount is required"), "refundAmount");
        outItemId = builder.outItemId;
        outSkuId = builder.outSkuId;
        outCertificateNos = List.copyOf(builder.outCertificateNos);
    }

    /**
     * 创建退款商品明细构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取商品编号。
     *
     * @return 商品编号
     */
    public String goodsId() {
        return goodsId;
    }

    /**
     * 获取商品退款金额。
     *
     * @return 商品退款金额，单位为分
     */
    public long refundAmount() {
        return refundAmount;
    }

    /**
     * 获取商家小程序商品 ID。
     *
     * @return 商家小程序商品 ID；没有时为 {@code null}
     */
    public @Nullable String outItemId() {
        return outItemId;
    }

    /**
     * 获取商家小程序 SKU ID。
     *
     * @return 商家小程序 SKU ID；没有时为 {@code null}
     */
    public @Nullable String outSkuId() {
        return outSkuId;
    }

    /**
     * 获取外部凭证编号。
     *
     * @return 不可变外部凭证编号列表
     */
    public List<String> outCertificateNos() {
        return outCertificateNos;
    }

    /** 退款商品 fluent 构建器。 */
    public static final class Builder {
        /** 构建期商户商品编号；未配置时构建失败。 */
        private @Nullable String goodsId;
        /** 构建期商品退款金额，单位为分；未配置时构建失败。 */
        private @Nullable Long refundAmount;
        /** 构建期商家小程序商品 ID；可选。 */
        private @Nullable String outItemId;
        /** 构建期商家小程序 SKU ID；可选。 */
        private @Nullable String outSkuId;
        /** 构建期外部凭证编号列表；默认为空，构建后复制为不可变列表。 */
        private final List<String> outCertificateNos = new ArrayList<>();

        /** 创建空退款商品构建器。 */
        private Builder() {
        }

        /**
         * 配置商品编号。
         *
         * @param value 商品编号
         * @return 当前构建器
         */
        public Builder goodsId(String value) {
            goodsId = value;
            return this;
        }

        /**
         * 配置商品退款金额。
         *
         * @param value 商品退款金额，单位为分
         * @return 当前构建器
         */
        public Builder refundAmount(long value) {
            refundAmount = value;
            return this;
        }

        /**
         * 配置商家小程序商品 ID。
         *
         * @param value 商家小程序商品 ID
         * @return 当前构建器
         */
        public Builder outItemId(String value) {
            outItemId = value;
            return this;
        }

        /**
         * 配置商家小程序 SKU ID。
         *
         * @param value 商家小程序 SKU ID
         * @return 当前构建器
         */
        public Builder outSkuId(String value) {
            outSkuId = value;
            return this;
        }

        /**
         * 添加外部凭证编号。
         *
         * @param value 外部凭证编号
         * @return 当前构建器
         */
        public Builder addOutCertificateNo(String value) {
            outCertificateNos.add(ValidationUtils.requireNotBlank(value, "outCertificateNo must not be blank"));
            return this;
        }

        /**
         * 校验参数并构建不可变退款商品明细。
         *
         * @return 不可变退款商品明细
         */
        public RefundGoodsDetail build() {
            return new RefundGoodsDetail(this);
        }
    }
}
