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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

/**
 * 微信支付 APIv3 Native、JSAPI、H5 下单共用的单笔订单业务参数。
 *
 * <p>该对象只承载订单本身的业务数据。{@code appid}、{@code mchid} 和
 * {@code notify_url} 由应用上下文及根客户端统一注入，调用方不能在单笔请求中覆盖。
 * 金额单位统一为分，构建完成后对象不可变。</p>
 *
 * <p>订单创建前应由业务系统先完成本地订单落库和幂等控制；本对象不负责本地订单创建、
 * 二维码生成、支付结果轮询或通知业务处理。</p>
 */
public final class PrepayRequest {
    /** 微信支付收银台展示的商品或服务描述，应能让用户明确本次支付内容。 */
    private final String description;
    /** 商户系统内唯一的订单号，用于查单、关单、支付通知及退款关联本地订单。 */
    private final String outTradeNo;
    /**
     * 订单允许支付的截止时间。未设置时以微信支付下单时间为起点，默认有效期为 7 天。
     */
    private final @Nullable OffsetDateTime timeExpire;
    /** 商户自定义数据包，可用于携带业务关联信息。 */
    private final @Nullable String attach;
    /** 微信支付优惠相关的订单标记。 */
    private final @Nullable String goodsTag;
    /** 是否在微信支付侧为用户展示电子发票入口。 */
    private final @Nullable Boolean supportFapiao;
    /** 订单应支付的总金额，单位为分。 */
    private final long amount;
    /** 可选订单原价、商品小票 ID 和单品列表。 */
    private final @Nullable PrepayDetail detail;
    /** 可选用户终端、商户设备和门店场景信息。 */
    private final @Nullable PrepaySceneInfo sceneInfo;
    /** 是否将订单标记为后续可能进行分账的订单。 */
    private final @Nullable Boolean profitSharing;

    /**
     * 使用构建期参数创建并校验下单请求。
     *
     * @param builder 已收集下单业务参数的构建器
     */
    private PrepayRequest(Builder builder) {
        // 1. 建立下单必填业务参数，保证最终请求可以关联本地订单。
        description = ValidationUtils.requireNotBlank(builder.description, "description is required");
        outTradeNo = WechatPayValidationUtils.requireOutTradeNo(
                ValidationUtils.requireNonNull(builder.outTradeNo, "outTradeNo is required"));
        amount = WechatPayValidationUtils.requirePositive(
                ValidationUtils.requireNonNull(builder.amount, "amount is required"), "amount");

        // 2. 复制可选业务参数；APPID、商户号和通知地址不属于单笔请求，由客户端发送前注入。
        timeExpire = builder.timeExpire;
        attach = builder.attach;
        goodsTag = builder.goodsTag;
        supportFapiao = builder.supportFapiao;
        detail = builder.detail;
        sceneInfo = builder.sceneInfo;
        profitSharing = builder.profitSharing;
    }

    /**
     * 创建下单请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回商品描述。
     *
     * @return 商品描述
     */
    public String description() {
        return description;
    }

    /**
     * 返回商户订单号。
     *
     * @return 商户订单号
     */
    public String outTradeNo() {
        return outTradeNo;
    }

    /**
     * 返回支付结束时间。
     *
     * @return 支付结束时间；未配置时为 {@code null}
     */
    public @Nullable OffsetDateTime timeExpire() {
        return timeExpire;
    }

    /**
     * 返回商户数据包。
     *
     * @return 商户数据包；未配置时为 {@code null}
     */
    public @Nullable String attach() {
        return attach;
    }

    /**
     * 返回订单优惠标记。
     *
     * @return 订单优惠标记；未配置时为 {@code null}
     */
    public @Nullable String goodsTag() {
        return goodsTag;
    }

    /**
     * 返回电子发票入口标识。
     *
     * @return 电子发票入口标识；未配置时为 {@code null}
     */
    public @Nullable Boolean supportFapiao() {
        return supportFapiao;
    }

    /**
     * 返回订单总金额。
     *
     * @return 订单总金额，单位为分
     */
    public long amount() {
        return amount;
    }

    /**
     * 返回商品详情。
     *
     * @return 商品详情；未配置时为 {@code null}
     */
    public @Nullable PrepayDetail detail() {
        return detail;
    }

    /**
     * 返回支付场景信息。
     *
     * @return 场景信息；未配置时为 {@code null}
     */
    public @Nullable PrepaySceneInfo sceneInfo() {
        return sceneInfo;
    }

    /**
     * 返回分账订单标识。
     *
     * @return 分账订单标识；未配置时为 {@code null}
     */
    public @Nullable Boolean profitSharing() {
        return profitSharing;
    }

    /**
     * 下单请求构建器。
     */
    public static final class Builder {
        /** 构建期商品描述，设置前为 {@code null}。 */
        private @Nullable String description;
        /** 构建期商户订单号，设置前为 {@code null}。 */
        private @Nullable String outTradeNo;
        /** 构建期支付截止时间。 */
        private @Nullable OffsetDateTime timeExpire;
        /** 构建期商户数据包。 */
        private @Nullable String attach;
        /** 构建期订单优惠标记。 */
        private @Nullable String goodsTag;
        /** 构建期电子发票入口开关。 */
        private @Nullable Boolean supportFapiao;
        /** 构建期订单总金额，设置前为 {@code null}，单位为分。 */
        private @Nullable Long amount;
        /** 构建期商品详情。 */
        private @Nullable PrepayDetail detail;
        /** 构建期支付场景信息。 */
        private @Nullable PrepaySceneInfo sceneInfo;
        /** 构建期分账订单标记。 */
        private @Nullable Boolean profitSharing;

        /** 创建空下单请求构建器。 */
        private Builder() {
        }

        /**
         * 配置商品描述。
         *
         * @param value 商品描述
         * @return 当前构建器
         */
        public Builder description(String value) {
            description = ValidationUtils.requireNotBlank(value, "description must not be blank");
            return this;
        }

        /**
         * 配置商户订单号。
         *
         * @param value 商户订单号
         * @return 当前构建器
         */
        public Builder outTradeNo(String value) {
            outTradeNo = WechatPayValidationUtils.requireOutTradeNo(value);
            return this;
        }

        /**
         * 配置支付结束时间。
         *
         * @param value 支付结束时间
         * @return 当前构建器
         */
        public Builder timeExpire(OffsetDateTime value) {
            timeExpire = ValidationUtils.requireNonNull(value,
                    "timeExpire must not be null");
            return this;
        }

        /**
         * 配置商户数据包。
         *
         * @param value 商户数据包
         * @return 当前构建器
         */
        public Builder attach(String value) {
            attach = ValidationUtils.requireNonNull(value, "attach must not be null");
            return this;
        }

        /**
         * 配置订单优惠标记。
         *
         * @param value 订单优惠标记
         * @return 当前构建器
         */
        public Builder goodsTag(String value) {
            goodsTag = ValidationUtils.requireNotBlank(value, "goodsTag must not be blank");
            return this;
        }

        /**
         * 配置是否开放电子发票入口。
         *
         * @param value 是否开放电子发票入口
         * @return 当前构建器
         */
        public Builder supportFapiao(boolean value) {
            supportFapiao = value;
            return this;
        }

        /**
         * 配置订单总金额。
         *
         * @param value 订单总金额，单位为分
         * @return 当前构建器
         */
        public Builder amount(long value) {
            amount = WechatPayValidationUtils.requirePositive(value, "amount");
            return this;
        }

        /**
         * 配置商品详情。
         *
         * @param value 商品详情
         * @return 当前构建器
         */
        public Builder detail(PrepayDetail value) {
            detail = ValidationUtils.requireNonNull(value, "detail must not be null");
            return this;
        }

        /**
         * 配置支付场景信息。
         *
         * @param value 支付场景信息
         * @return 当前构建器
         */
        public Builder sceneInfo(PrepaySceneInfo value) {
            sceneInfo = ValidationUtils.requireNonNull(value, "sceneInfo must not be null");
            return this;
        }

        /**
         * 配置是否标记为分账订单。
         *
         * @param value 是否标记为分账订单
         * @return 当前构建器
         */
        public Builder profitSharing(boolean value) {
            profitSharing = value;
            return this;
        }

        /**
         * 校验并创建不可变请求。
         *
         * @return 下单请求
         */
        public PrepayRequest build() {
            return new PrepayRequest(this);
        }
    }
}
