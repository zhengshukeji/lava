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

package com.zhengshuyun.lava.pay.wechat.transaction;

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
 * 普通支付订单状态，供查单结果和支付成功通知复用。
 *
 * @param appid 下单应用 ID
 * @param mchid 商户号
 * @param outTradeNo 商户订单号
 * @param transactionId 微信支付订单号
 * @param tradeType 交易类型原始值
 * @param tradeState 交易状态原始值
 * @param tradeStateDesc 交易状态描述
 * @param bankType 银行类型
 * @param attach 商户数据包
 * @param successTime 支付完成时间
 * @param payer 支付者信息
 * @param amount 订单金额
 * @param sceneInfo 场景信息
 * @param promotionDetail 优惠详情
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Transaction(
        @JsonProperty("appid") String appid,
        @JsonProperty("mchid") String mchid,
        @JsonProperty("out_trade_no") String outTradeNo,
        @JsonProperty("transaction_id") @Nullable String transactionId,
        @JsonProperty("trade_type") @Nullable String tradeType,
        @JsonProperty("trade_state") String tradeState,
        @JsonProperty("trade_state_desc") String tradeStateDesc,
        @JsonProperty("bank_type") @Nullable String bankType,
        @JsonProperty("attach") @Nullable String attach,
        @JsonProperty("success_time") @Nullable OffsetDateTime successTime,
        @JsonProperty("payer") @Nullable Payer payer,
        @JsonProperty("amount") @Nullable Amount amount,
        @JsonProperty("scene_info") @Nullable SceneInfo sceneInfo,
        @JsonProperty("promotion_detail") @Nullable List<PromotionDetail> promotionDetail
) {

    /**
     * 复制优惠列表以保持记录不可变。
     */
    public Transaction {
        if (promotionDetail != null) {
            promotionDetail = List.copyOf(promotionDetail);
        }
    }

    /**
     * 判断订单是否已经支付成功。
     *
     * @return 交易状态为 {@link TradeState#SUCCESS} 时返回 {@code true}
     */
    public boolean paid() {
        return TradeState.SUCCESS.equals(tradeState);
    }

    /**
     * 使用后端可信订单记录核对应用、商户订单号和订单金额。
     *
     * <p>微信支付查单只保证在支付成功时返回 {@code amount}，已关闭等未付款状态的响应可能不含金额。
     * 因此支付成功的交易必须带有一致的金额；未付款交易缺少金额时只核对应用和商户订单号，
     * 但只要返回了金额就必须一致。</p>
     *
     * @param expectedAppid      可信应用 ID
     * @param expectedOutTradeNo 可信商户订单号
     * @param expectedTotal      可信订单总金额，单位为分
     * @return 当前交易
     * @throws WechatPaySecurityException 任一关键字段不匹配
     */
    public Transaction requireOrder(
            String expectedAppid,
            String expectedOutTradeNo,
            long expectedTotal
    ) {
        ValidationUtils.requireNotBlank(expectedAppid, "expectedAppid must not be blank");
        ValidationUtils.requireNotBlank(
                expectedOutTradeNo,
                "expectedOutTradeNo must not be blank"
        );
        WechatPayValidationUtils.requirePositive(expectedTotal, "expectedTotal");
        if (!expectedAppid.equals(appid) || !expectedOutTradeNo.equals(outTradeNo)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        // 未付款交易可能不返回金额，此时没有金额可核对；已付款交易缺少金额视为不一致
        if (amount == null) {
            if (paid()) {
                throw new WechatPaySecurityException(WechatPaySecurityFailure.RESPONSE_MISMATCH);
            }
            return this;
        }
        if (amount.total == null
                || amount.total != expectedTotal
                || !"CNY".equals(amount.currency)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        return this;
    }

    /**
     * 使用后端已保存的微信侧标识完整核对支付成功订单。
     *
     * <p>适用于重复通知或主动查单等本地已经保存微信支付订单号和付款人 OpenID 的场景。</p>
     *
     * @param expectedAppid         可信应用 ID
     * @param expectedOutTradeNo    可信商户订单号
     * @param expectedTransactionId 可信微信支付订单号
     * @param expectedOpenid        可信付款人 OpenID
     * @param expectedTotal         可信订单总金额，单位为分
     * @return 当前交易
     * @throws WechatPaySecurityException 任一关键字段不匹配
     */
    public Transaction requirePaidOrder(
            String expectedAppid,
            String expectedOutTradeNo,
            String expectedTransactionId,
            String expectedOpenid,
            long expectedTotal
    ) {
        requireOrder(
                expectedAppid,
                expectedOutTradeNo,
                expectedTotal
        );
        expectedTransactionId = ValidationUtils.requireNotBlank(
                expectedTransactionId, "expectedTransactionId must not be blank");
        expectedOpenid = ValidationUtils.requireNotBlank(expectedOpenid, "expectedOpenid must not be blank");
        if (!paid()
                || !expectedTransactionId.equals(transactionId)
                || payer == null
                || !expectedOpenid.equals(payer.openid)) {
            throw new WechatPaySecurityException(
                    WechatPaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        return this;
    }

    /**
     * 支付者信息。
     *
     * @param openid 用户在当前 APPID 下的标识
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payer(@JsonProperty("openid") @Nullable String openid) {
    }

    /**
     * 订单金额。
     *
     * @param total 订单总金额，单位为分
     * @param payerTotal 用户实际支付金额，单位为分
     * @param currency 订单币种
     * @param payerCurrency 用户支付币种
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            @JsonProperty("total") @Nullable Long total,
            @JsonProperty("payer_total") @Nullable Long payerTotal,
            @JsonProperty("currency") @Nullable String currency,
            @JsonProperty("payer_currency") @Nullable String payerCurrency
    ) {
    }

    /**
     * 支付场景信息。
     *
     * @param deviceId 商户端设备号
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SceneInfo(@JsonProperty("device_id") @Nullable String deviceId) {
    }

    /**
     * 代金券优惠详情。
     *
     * @param couponId 券 ID
     * @param name 优惠名称
     * @param scope 优惠范围
     * @param type 优惠资金类型
     * @param amount 券面额
     * @param stockId 活动 ID
     * @param wechatpayContribute 微信出资金额
     * @param merchantContribute 商户出资金额
     * @param otherContribute 其他出资金额
     * @param currency 优惠币种
     * @param goodsDetail 单品优惠详情
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromotionDetail(
            @JsonProperty("coupon_id") String couponId,
            @JsonProperty("name") @Nullable String name,
            @JsonProperty("scope") @Nullable String scope,
            @JsonProperty("type") @Nullable String type,
            @JsonProperty("amount") long amount,
            @JsonProperty("stock_id") @Nullable String stockId,
            @JsonProperty("wechatpay_contribute") @Nullable Long wechatpayContribute,
            @JsonProperty("merchant_contribute") @Nullable Long merchantContribute,
            @JsonProperty("other_contribute") @Nullable Long otherContribute,
            @JsonProperty("currency") @Nullable String currency,
            @JsonProperty("goods_detail") @Nullable List<PromotionGoodsDetail> goodsDetail
    ) {

        /**
         * 复制优惠商品列表。
         */
        public PromotionDetail {
            if (goodsDetail != null) {
                goodsDetail = List.copyOf(goodsDetail);
            }
        }
    }

    /**
     * 优惠涉及的单品。
     *
     * @param goodsId 商品编码
     * @param quantity 商品数量
     * @param unitPrice 商品单价，单位为分
     * @param discountAmount 商品优惠金额，单位为分
     * @param goodsRemark 商品备注
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromotionGoodsDetail(
            @JsonProperty("goods_id") String goodsId,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("unit_price") long unitPrice,
            @JsonProperty("discount_amount") long discountAmount,
            @JsonProperty("goods_remark") @Nullable String goodsRemark
    ) {
    }
}
