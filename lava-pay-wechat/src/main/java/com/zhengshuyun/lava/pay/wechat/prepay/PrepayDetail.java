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

package com.zhengshuyun.lava.pay.wechat.prepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 下单共用的可选商品详情。
 *
 * @param costPrice 订单原价，单位为分
 * @param invoiceId 商品小票 ID
 * @param goodsDetail 单品列表
 */
public record PrepayDetail(
        @JsonProperty("cost_price") @Nullable Long costPrice,
        @JsonProperty("invoice_id") @Nullable String invoiceId,
        @JsonProperty("goods_detail") @Nullable List<GoodsDetail> goodsDetail) {

    /**
     * 校验商品详情并复制单品列表。
     */
    public PrepayDetail {
        // 1. 分别校验可选的订单原价和小票标识。
        if (costPrice != null) {
            WechatPayValidationUtils.requirePositive(costPrice, "costPrice");
        }
        if (invoiceId != null) {
            ValidationUtils.requireNotBlank(invoiceId, "invoiceId must not be blank");
        }

        // 2. 单品列表至少包含一项，并复制为不可变列表以隔离调用方后续修改。
        if (goodsDetail != null) {
            ValidationUtils.requireNotEmpty(goodsDetail,
                    "goodsDetail must contain at least one item");
            goodsDetail = List.copyOf(goodsDetail);
        }

        // 3. 商品详情出现时必须实际携带至少一种明细，不能编码为空 JSON 对象。
        ValidationUtils.requireTrue(costPrice != null || invoiceId != null
                        || goodsDetail != null,
                "detail must contain at least one field");
    }

    /**
     * 创建商品详情构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 商品详情构建器。
     */
    public static final class Builder {
        /** 构建期订单原价。 */
        private @Nullable Long costPrice;
        /** 构建期商品小票 ID。 */
        private @Nullable String invoiceId;
        /** 构建期单品列表。 */
        private final List<GoodsDetail> goodsDetail = new ArrayList<>();

        /** 创建空商品详情构建器。 */
        private Builder() {
        }

        /**
         * 配置订单原价。
         *
         * @param value 订单原价，单位为分
         * @return 当前构建器
         */
        public Builder costPrice(long value) {
            costPrice = WechatPayValidationUtils.requirePositive(value, "costPrice");
            return this;
        }

        /**
         * 配置商品小票 ID。
         *
         * @param value 商品小票 ID
         * @return 当前构建器
         */
        public Builder invoiceId(String value) {
            invoiceId = ValidationUtils.requireNotBlank(value, "invoiceId must not be blank");
            return this;
        }

        /**
         * 追加一项单品信息。
         *
         * @param value 待追加单品
         * @return 当前构建器
         */
        public Builder addGoodsDetail(GoodsDetail value) {
            goodsDetail.add(ValidationUtils.requireNonNull(value,
                    "goodsDetail must not be null"));
            return this;
        }

        /**
         * 校验并创建不可变商品详情。
         *
         * @return 商品详情
         */
        public PrepayDetail build() {
            return new PrepayDetail(costPrice, invoiceId,
                    goodsDetail.isEmpty() ? null : List.copyOf(goodsDetail));
        }
    }

    /**
     * 下单商品详情中的单品信息。
     *
     * @param merchantGoodsId 商户侧商品编码
     * @param wechatpayGoodsId 微信支付商品编码
     * @param goodsName 商品名称
     * @param quantity 商品数量
     * @param unitPrice 商品单价，单位为分
     */
    public record GoodsDetail(
            @JsonProperty("merchant_goods_id") String merchantGoodsId,
            @JsonProperty("wechatpay_goods_id") @Nullable String wechatpayGoodsId,
            @JsonProperty("goods_name") @Nullable String goodsName,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("unit_price") long unitPrice
    ) {

        /**
         * 校验单品信息。
         */
        public GoodsDetail {
            merchantGoodsId = WechatPayValidationUtils.requireMerchantGoodsId(
                    merchantGoodsId);
            if (wechatpayGoodsId != null) {
                ValidationUtils.requireNotBlank(wechatpayGoodsId, "wechatpayGoodsId must not be blank");
            }
            if (goodsName != null) {
                ValidationUtils.requireNotBlank(goodsName, "goodsName must not be blank");
            }
            WechatPayValidationUtils.requirePositive(quantity, "quantity");
            WechatPayValidationUtils.requirePositive(unitPrice, "unitPrice");
        }

        /**
         * 创建单品信息构建器。
         *
         * @return 新构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 单品信息构建器。
         */
        public static final class Builder {
            /** 构建期商户侧商品编码。 */
            private @Nullable String merchantGoodsId;
            /** 构建期微信支付商品编码。 */
            private @Nullable String wechatpayGoodsId;
            /** 构建期商品名称。 */
            private @Nullable String goodsName;
            /** 构建期商品数量。 */
            private @Nullable Long quantity;
            /** 构建期商品单价。 */
            private @Nullable Long unitPrice;

            /** 创建空单品信息构建器。 */
            private Builder() {
            }

            /**
             * 配置商户侧商品编码。
             *
             * @param value 商户侧商品编码
             * @return 当前构建器
             */
            public Builder merchantGoodsId(String value) {
                merchantGoodsId = value;
                return this;
            }

            /**
             * 配置微信支付商品编码。
             *
             * @param value 微信支付商品编码
             * @return 当前构建器
             */
            public Builder wechatpayGoodsId(String value) {
                wechatpayGoodsId = value;
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
            public Builder unitPrice(long value) {
                unitPrice = value;
                return this;
            }

            /**
             * 校验并创建不可变单品信息。
             *
             * @return 单品信息
             */
            public GoodsDetail build() {
                return new GoodsDetail(
                        ValidationUtils.requireNonNull(
                                merchantGoodsId,
                                "merchantGoodsId is required"
                        ),
                        wechatpayGoodsId,
                        goodsName,
                        ValidationUtils.requireNonNull(quantity, "quantity is required"),
                        ValidationUtils.requireNonNull(unitPrice, "unitPrice is required")
                );
            }
        }
    }
}
