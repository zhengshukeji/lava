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

package com.zhengshuyun.lava.pay.wechat.jsapi;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayProtocolException;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayPrepayUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayRequest;

import java.net.URI;

/**
 * 微信支付 APIv3 普通商户 JSAPI 支付入口，公众号网页与小程序共用。
 *
 * <p>实例固定绑定一个 APPID 和支付结果通知地址。公众号与小程序的 APPID 不同，
 * 应分别通过 {@code WechatPayClient.application(...)} 创建两个应用上下文；
 * 付款用户的 OpenID 必须是在同一 APPID 下获取的。</p>
 */
public final class JsapiPayClient {
    /**
     * JSAPI 下单接口的固定 API 路径。
     */
    private static final String PREPAY_PATH = "/v3/pay/transactions/jsapi";
    /**
     * 调起支付固定使用的签名类型。
     */
    private static final String SIGN_TYPE = "RSA";

    /**
     * 共享协议能力和根客户端关闭状态所在的传输层。
     */
    private final WechatPayTransport transport;
    /**
     * 当前应用上下文固定绑定的 APPID，同时用于下单和调起支付签名。
     */
    private final String appid;
    /**
     * 当前应用上下文固定使用的支付结果通知地址。
     */
    private final URI notifyUrl;

    /**
     * 由应用上下文创建 JSAPI 支付入口。
     *
     * @param transport 共享协议传输层
     * @param appid     应用 ID
     * @param notifyUrl 固定支付通知地址
     */
    public JsapiPayClient(WechatPayTransport transport,
                          String appid,
                          URI notifyUrl) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.appid = ValidationUtils.requireNotBlank(appid, "appid");
        this.notifyUrl = ValidationUtils.requireNonNull(notifyUrl, "notifyUrl");
    }

    /**
     * 创建 JSAPI 预支付订单。
     *
     * @param openid  付款用户在当前 APPID 下的 OpenID
     * @param request 下单业务参数
     * @return 已验签的下单结果
     * @throws IllegalArgumentException OpenID 为空白或请求为空
     * @throws WechatPayException       协议调用失败
     */
    public JsapiPrepayResponse prepay(String openid, PrepayRequest request) {
        // 1. 先确认根客户端未关闭，防止关闭后继续创建支付订单。
        transport.ensureOpen();
        ValidationUtils.requireNotBlank(openid, "openid must not be blank");
        ValidationUtils.requireNonNull(request, "request must not be null");

        // 2. 与 Native 共用载荷组装，JSAPI 仅额外携带 payer.openid。
        Object payload = WechatPayPrepayUtils.payload(
                appid, transport.mchid(), notifyUrl, request, openid);

        // 3. 由传输层完成签名、发送、响应验签和解析；prepay_id 是唯一产出，缺失说明协议已变化
        JsapiPrepayResponse response = transport.post(
                transport.endpoint(PREPAY_PATH), payload, JsapiPrepayResponse.class);
        if (response.prepayId() == null || response.prepayId().isBlank()) {
            throw new WechatPayProtocolException("微信支付 JSAPI 下单响应缺少 prepay_id");
        }
        return response;
    }

    /**
     * 为已有预支付会话生成前端调起支付参数，不发起网络请求。
     *
     * <p>prepay_id 有效期为 2 小时，用户取消后在有效期内重新支付时，可直接用原 prepay_id
     * 重新签名，无需再次下单。</p>
     *
     * @param prepayId 下单返回的预支付交易会话标识
     * @return 可直接交给前端的已签名调起支付参数
     * @throws IllegalArgumentException prepayId 为空白
     * @throws IllegalStateException    根客户端已经关闭
     */
    public JsapiPayParams requestPayment(String prepayId) {
        ValidationUtils.requireNotBlank(prepayId, "prepayId must not be blank");
        String packageValue = "prepay_id=" + prepayId;
        // 签名原文依次为 appId、timeStamp、nonceStr、package，各占一行
        WechatPayTransport.PaymentSignature signature = transport.signPayment(appid, packageValue);
        return new JsapiPayParams(
                appid,
                signature.timestamp(),
                signature.nonce(),
                packageValue,
                SIGN_TYPE,
                signature.signature()
        );
    }

    /**
     * 创建 JSAPI 预支付订单并直接生成前端调起支付参数。
     *
     * @param openid  付款用户在当前 APPID 下的 OpenID
     * @param request 下单业务参数
     * @return 可直接交给前端的已签名调起支付参数
     * @throws IllegalArgumentException OpenID 为空白或请求为空
     * @throws WechatPayException       协议调用失败
     */
    public JsapiPayParams prepayWithRequestPayment(String openid, PrepayRequest request) {
        return requestPayment(prepay(openid, request).prepayId());
    }
}
