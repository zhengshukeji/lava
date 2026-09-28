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

package com.zhengshuyun.lava.pay.wechat.h5;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayProtocolException;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayPrepayUtils;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepayRequest;
import com.zhengshuyun.lava.pay.wechat.prepay.PrepaySceneInfo;

import java.net.URI;

/**
 * 微信支付 APIv3 普通商户 H5 支付入口，用于微信外的手机浏览器。
 *
 * <p>实例固定绑定一个 APPID 和支付结果通知地址。H5 支付需要在商户平台单独开通并配置支付域名；
 * 微信内打开的网页应改用 JSAPI 支付。</p>
 */
public final class H5PayClient {
    /**
     * H5 下单接口的固定 API 路径。
     */
    private static final String PREPAY_PATH = "/v3/pay/transactions/h5";

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
     * 由应用上下文创建 H5 支付入口。
     *
     * @param transport 共享协议传输层
     * @param appid     应用 ID
     * @param notifyUrl 固定支付通知地址
     */
    public H5PayClient(WechatPayTransport transport,
                       String appid,
                       URI notifyUrl) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.appid = ValidationUtils.requireNotBlank(appid, "appid");
        this.notifyUrl = ValidationUtils.requireNonNull(notifyUrl, "notifyUrl");
    }

    /**
     * 创建 H5 预支付订单并返回支付跳转链接。
     *
     * @param request 下单业务参数，必须包含带 {@code h5Info} 的场景信息
     * @return 已验签的下单结果
     * @throws IllegalArgumentException 请求为空，或缺少场景信息、H5 场景信息
     * @throws WechatPayException       协议调用失败
     */
    public H5PrepayResponse prepay(PrepayRequest request) {
        // 1. 先确认根客户端未关闭，再校验 H5 协议必填的场景信息（用户终端 IP 已由场景信息自身保证）
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        PrepaySceneInfo sceneInfo = ValidationUtils.requireNonNull(
                request.sceneInfo(), "sceneInfo is required for H5 prepay");
        ValidationUtils.requireNonNull(
                sceneInfo.h5Info(), "sceneInfo.h5Info is required for H5 prepay");

        // 2. 与 Native 共用载荷组装，H5 场景信息随 scene_info 一并输出。
        Object payload = WechatPayPrepayUtils.payload(
                appid, transport.mchid(), notifyUrl, request, null);

        // 3. 由传输层完成签名、发送、响应验签和解析；h5_url 是唯一产出，缺失说明协议已变化
        H5PrepayResponse response = transport.post(
                transport.endpoint(PREPAY_PATH), payload, H5PrepayResponse.class);
        if (response.h5Url() == null) {
            throw new WechatPayProtocolException("微信支付 H5 下单响应缺少 h5_url");
        }
        return response;
    }
}
