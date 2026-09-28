/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.pay.alipay.order.GoodsDetail;
import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * 电脑网站、手机网站与小程序下单共用的业务字段转换。
 *
 * <p>三种下单的 {@code biz_content} 在时效、渠道、回传参数和商品明细上编码规则一致，
 * 集中在此处转换，避免各下单入口各写一份。</p>
 */
public final class AlipayOrderUtils {
    /** 绝对过期时间的固定格式，精确到秒。 */
    private static final DateTimeFormatter TIME_EXPIRE = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss");

    private AlipayOrderUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 格式化绝对过期时间。
     *
     * @param value 按 GMT+8 解释的绝对过期时间
     * @return {@code yyyy-MM-dd HH:mm:ss} 文本；未设置时为 {@code null}
     */
    public static @Nullable String timeExpire(@Nullable LocalDateTime value) {
        return value == null ? null : TIME_EXPIRE.format(value);
    }

    /**
     * 将相对有效期转换为协议要求的整分钟文本。
     *
     * @param value 已校验为整分钟的相对有效期
     * @return 形如 {@code 90m} 的文本；未设置时为 {@code null}
     */
    public static @Nullable String timeoutExpress(@Nullable Duration value) {
        return value == null ? null : value.toMinutes() + "m";
    }

    /**
     * 将支付渠道集合拼接为逗号分隔文本。
     *
     * @param channels 已校验不含逗号的渠道集合
     * @return 逗号分隔文本；集合为空时为 {@code null}
     */
    public static @Nullable String channels(Collection<String> channels) {
        return channels.isEmpty() ? null : String.join(",", channels);
    }

    /**
     * 按支付宝要求对业务回传参数进行一次 URL 编码。
     *
     * @param value 原始回传参数
     * @return 已编码文本；未设置时为 {@code null}
     */
    public static @Nullable String passbackParams(@Nullable String value) {
        return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 将公开商品明细映射为协议载荷，价格由分转换为元。
     *
     * @param goods 不可变商品明细列表
     * @return 协议载荷列表；没有商品时为 {@code null}，从 JSON 中省略
     */
    public static @Nullable List<?> goodsDetail(List<GoodsDetail> goods) {
        return goods.isEmpty() ? null : goods.stream().map(GoodsPayload::from).toList();
    }

    /**
     * 下单载荷中的单个商品明细。
     *
     * @param goodsId        商户商品编号，最长 64 字符
     * @param goodsName      商品名称，最长 256 字符
     * @param quantity       商品数量，必须大于零
     * @param price          商品单价，单位为元、固定保留两位小数
     * @param alipayGoodsId  可选支付宝商品编号，最长 32 字符
     * @param goodsCategory  可选商品类目，最长 24 字符
     * @param categoriesTree 可选商品类目树，最长 128 字符
     * @param body           可选商品说明，最长 400 字符
     * @param showUrl        可选商品展示绝对地址，最长 400 字符
     */
    private record GoodsPayload(
            @JsonProperty("goods_id") String goodsId,
            @JsonProperty("goods_name") String goodsName,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("price") String price,
            @JsonProperty("alipay_goods_id") @Nullable String alipayGoodsId,
            @JsonProperty("goods_category") @Nullable String goodsCategory,
            @JsonProperty("categories_tree") @Nullable String categoriesTree,
            @JsonProperty("body") @Nullable String body,
            @JsonProperty("show_url") @Nullable String showUrl
    ) {
        /**
         * 将已校验的公开商品模型映射为协议载荷。
         *
         * @param value 不可变商品明细，价格单位为分
         * @return 价格已转换为元、展示地址已转换为 ASCII 文本的协议载荷
         */
        private static GoodsPayload from(GoodsDetail value) {
            return new GoodsPayload(
                    value.goodsId(),
                    value.goodsName(),
                    value.quantity(),
                    AlipayMoneyUtils.formatPositive(value.price()),
                    value.alipayGoodsId(),
                    value.goodsCategory(),
                    value.categoriesTree(),
                    value.body(),
                    value.showUrl() == null ? null : value.showUrl().toASCIIString()
            );
        }
    }
}
