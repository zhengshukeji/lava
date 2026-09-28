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

package com.zhengshuyun.lava.pay.wechat.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayDetail;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayRequest;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepaySceneInfo;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Native、JSAPI、H5 下单共用的协议载荷组装。
 *
 * <p>三种下单的请求体只差 {@code payer} 与 {@code scene_info.h5_info}，集中在此处组装，
 * 避免截止时间格式、金额币种和分账标记在各下单入口各写一份。</p>
 */
public final class WechatPayPrepayUtils {
    /**
     * 支付截止时间的协议格式 {@code yyyy-MM-DDTHH:mm:ss+TIMEZONE}，精确到秒。
     *
     * <p>不能交给 Jackson 默认序列化：默认格式会带上小数秒（Linux 上 {@code Instant.now()}
     * 精确到纳秒），微信支付会以 {@code PARAM_ERROR} 拒绝；UTC 偏移也会被写成 {@code Z}
     * 而不是文档要求的 {@code +00:00}。</p>
     */
    private static final DateTimeFormatter TIME_EXPIRE = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ssxxx");

    private WechatPayPrepayUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将固定应用配置与单笔业务参数合成为最终下单载荷，业务请求不能覆盖商户级配置。
     *
     * @param appid     应用上下文固定绑定的应用 ID
     * @param mchid     根客户端固定绑定的商户号
     * @param notifyUrl 应用上下文固定使用的支付结果通知地址
     * @param request   单笔订单业务参数
     * @param openid    JSAPI 下单的付款用户 OpenID；Native 与 H5 下单传 {@code null}
     * @return 可直接交给传输层编码的请求载荷
     */
    public static Object payload(String appid,
                                 String mchid,
                                 URI notifyUrl,
                                 PrepayRequest request,
                                 @Nullable String openid) {
        Boolean profitSharing = request.profitSharing();
        SettleInfo settleInfo = profitSharing == null ? null : new SettleInfo(profitSharing);
        // 截止时间按协议格式输出，秒以下直接舍去，保留调用方给出的时区偏移
        OffsetDateTime timeExpire = request.timeExpire();
        String formattedTimeExpire = timeExpire == null ? null : TIME_EXPIRE.format(timeExpire);
        return new PrepayPayload(
                appid,
                mchid,
                request.description(),
                request.outTradeNo(),
                formattedTimeExpire,
                request.attach(),
                notifyUrl.toASCIIString(),
                request.goodsTag(),
                request.supportFapiao(),
                new Amount(request.amount(), "CNY"),
                openid == null ? null : new Payer(openid),
                request.detail(),
                request.sceneInfo(),
                settleInfo
        );
    }

    /**
     * 下单接口的内部请求载荷。
     *
     * <p>{@code appid}、{@code mchid} 和 {@code notify_url} 来自固定应用上下文及根客户端，
     * 不接受 {@link PrepayRequest} 覆盖；其余字段仅承载已经完成校验的业务参数。</p>
     *
     * @param appid         应用上下文固定绑定的应用 ID
     * @param mchid         根客户端固定绑定的商户号
     * @param description   用户在微信支付侧看到的商品或服务描述
     * @param outTradeNo    商户订单号，用于创建并关联微信支付订单
     * @param timeExpire    可选支付截止时间，已按协议格式精确到秒
     * @param attach        可选商户自定义数据包
     * @param notifyUrl     应用上下文固定使用的支付结果通知地址
     * @param goodsTag      可选订单优惠标记
     * @param supportFapiao 可选电子发票入口开关
     * @param amount        必填订单金额
     * @param payer         JSAPI 下单必填的付款用户，其余下单为 {@code null}
     * @param detail        可选订单商品明细
     * @param sceneInfo     用户终端和门店场景；H5 下单必填
     * @param settleInfo    可选分账结算标记
     */
    private record PrepayPayload(
            @JsonProperty("appid") String appid,
            @JsonProperty("mchid") String mchid,
            @JsonProperty("description") String description,
            @JsonProperty("out_trade_no") String outTradeNo,
            @JsonProperty("time_expire") @Nullable String timeExpire,
            @JsonProperty("attach") @Nullable String attach,
            @JsonProperty("notify_url") String notifyUrl,
            @JsonProperty("goods_tag") @Nullable String goodsTag,
            @JsonProperty("support_fapiao") @Nullable Boolean supportFapiao,
            @JsonProperty("amount") Amount amount,
            @JsonProperty("payer") @Nullable Payer payer,
            @JsonProperty("detail") @Nullable PrepayDetail detail,
            @JsonProperty("scene_info") @Nullable PrepaySceneInfo sceneInfo,
            @JsonProperty("settle_info") @Nullable SettleInfo settleInfo
    ) {
    }

    /**
     * 下单协议要求的订单金额对象。
     *
     * @param total    订单总金额，单位为分
     * @param currency 固定为人民币 {@code CNY}
     */
    private record Amount(
            @JsonProperty("total") long total,
            @JsonProperty("currency") String currency) {
    }

    /**
     * JSAPI 下单的付款用户。
     *
     * @param openid 用户在当前 APPID 下的唯一标识
     */
    private record Payer(@JsonProperty("openid") String openid) {
    }

    /**
     * 下单协议要求的结算信息对象。
     *
     * @param profitSharing 是否将订单标记为分账订单
     */
    private record SettleInfo(
            @JsonProperty("profit_sharing") boolean profitSharing) {
    }
}
