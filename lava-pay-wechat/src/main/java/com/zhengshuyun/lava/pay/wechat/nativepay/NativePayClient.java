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

package com.zhengshuyun.lava.pay.wechat.nativepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayProtocolException;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 微信支付 APIv3 普通商户 Native 支付入口。
 *
 * <p>实例固定绑定一个 APPID 和支付结果通知地址；单笔下单只接收业务参数，避免调用方误传商户号、
 * APPID 或通知地址。二维码图片渲染、订单幂等和支付结果轮询不属于本客户端职责。</p>
 */
public final class NativePayClient {
    /**
     * Native 下单接口的固定 API 路径。
     */
    private static final String PREPAY_PATH = "/v3/pay/transactions/native";
    /**
     * 支付截止时间的协议格式 {@code yyyy-MM-DDTHH:mm:ss+TIMEZONE}，精确到秒。
     *
     * <p>不能交给 Jackson 默认序列化：默认格式会带上小数秒（Linux 上 {@code Instant.now()}
     * 精确到纳秒），微信支付会以 {@code PARAM_ERROR} 拒绝；UTC 偏移也会被写成 {@code Z}
     * 而不是文档要求的 {@code +00:00}。</p>
     */
    private static final DateTimeFormatter TIME_EXPIRE = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ssxxx");

    /**
     * 共享协议能力和根客户端关闭状态所在的传输层。
     */
    private final WechatPayTransport transport;
    /**
     * 当前应用上下文固定绑定的 APPID。
     */
    private final String appid;
    /**
     * 当前应用上下文固定使用的支付结果通知地址。
     */
    private final URI notifyUrl;

    /**
     * 由应用上下文创建 Native 支付入口。
     *
     * @param transport   共享协议传输层
     * @param appid     应用 ID
     * @param notifyUrl 固定支付通知地址
     */
    public NativePayClient(WechatPayTransport transport,
                           String appid,
                           URI notifyUrl) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.appid = ValidationUtils.requireNotBlank(appid, "appid");
        this.notifyUrl = ValidationUtils.requireNonNull(notifyUrl, "notifyUrl");
    }

    /**
     * 创建 Native 预支付订单并返回二维码链接。
     *
     * @param request 下单业务参数
     * @return 已验签的下单结果
     * @throws WechatPayException 协议调用失败
     */
    public NativePrepayResponse prepay(NativePrepayRequest request) {
        // 1. 先确认根客户端未关闭，防止关闭后继续创建支付订单。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");

        // 2. 将固定应用配置与单笔业务参数合成为最终协议载荷，业务请求不能覆盖商户级配置。
        Boolean profitSharing = request.profitSharing();
        SettleInfo settleInfo = profitSharing == null
                ? null : new SettleInfo(profitSharing);
        // 截止时间按协议格式输出，秒以下直接舍去，保留调用方给出的时区偏移
        OffsetDateTime timeExpire = request.timeExpire();
        String formattedTimeExpire = timeExpire == null ? null : TIME_EXPIRE.format(timeExpire);
        PrepayPayload payload = new PrepayPayload(
                appid,
                transport.mchid(),
                request.description(),
                request.outTradeNo(),
                formattedTimeExpire,
                request.attach(),
                notifyUrl.toASCIIString(),
                request.goodsTag(),
                request.supportFapiao(),
                new Amount(request.amount(), "CNY"),
                request.detail(),
                request.sceneInfo(),
                settleInfo
        );

        // 3. 由传输层使用最终载荷完成 JSON 编码、请求签名、发送、响应验签和结果解析
        NativePrepayResponse response = transport.post(
                transport.endpoint(PREPAY_PATH), payload, NativePrepayResponse.class);
        // code_url 是下单的唯一产出，缺失说明协议已变化，不能把 null 交给调用方
        if (response.codeUrl() == null) {
            throw new WechatPayProtocolException("微信支付 Native 下单响应缺少 code_url");
        }
        return response;
    }

    /**
     * Native 下单接口的内部请求载荷。
     *
     * <p>{@code appid}、{@code mchid} 和 {@code notify_url} 来自固定应用上下文及根客户端，
     * 不接受 {@link NativePrepayRequest} 覆盖；其余字段仅承载已经完成校验的业务参数。</p>
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
     * @param detail        可选订单商品明细
     * @param sceneInfo     可选用户终端和门店场景
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
            @JsonProperty("detail") @Nullable NativePrepayDetail detail,
            @JsonProperty("scene_info") @Nullable NativePrepaySceneInfo sceneInfo,
            @JsonProperty("settle_info") @Nullable SettleInfo settleInfo
    ) {
    }

    /**
     * Native 下单协议要求的订单金额对象。
     *
     * @param total    订单总金额，单位为分
     * @param currency 固定为人民币 {@code CNY}
     */
    private record Amount(
            @JsonProperty("total") long total,
            @JsonProperty("currency") String currency) {
    }

    /**
     * Native 下单协议要求的结算信息对象。
     *
     * @param profitSharing 是否将订单标记为分账订单
     */
    private record SettleInfo(
            @JsonProperty("profit_sharing") boolean profitSharing) {
    }
}
