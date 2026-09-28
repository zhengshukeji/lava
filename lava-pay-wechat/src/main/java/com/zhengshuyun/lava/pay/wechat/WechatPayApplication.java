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

package com.zhengshuyun.lava.pay.wechat;

import com.zhengshuyun.lava.pay.wechat.h5.H5PayClient;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.jsapi.JsapiPayClient;
import com.zhengshuyun.lava.pay.wechat.nativepay.NativePayClient;

import java.net.URI;

/**
 * 绑定一个 APPID 与支付通知地址的轻量微信支付应用上下文。
 *
 * <p>同一商户号可创建多个应用上下文，以隔离不同 APPID 的下单参数，例如公众号与小程序各用一个；
 * 上下文不持有独立连接池或商户凭证，而是复用创建它的 {@link WechatPayClient} 协议资源。</p>
 */
public final class WechatPayApplication {
    /**
     * 已与当前商户号绑定的应用 ID。
     */
    private final String appid;
    /**
     * 当前应用固定使用的支付结果通知地址。
     */
    private final URI notifyUrl;
    /**
     * 复用当前 APPID 与通知地址的 Native 支付入口。
     */
    private final NativePayClient nativePayClient;
    /**
     * 复用当前 APPID 与通知地址的 JSAPI 支付入口。
     */
    private final JsapiPayClient jsapiPayClient;
    /**
     * 复用当前 APPID 与通知地址的 H5 支付入口。
     */
    private final H5PayClient h5PayClient;

    /**
     * 由微信支付根客户端创建应用上下文。
     *
     * @param transport   共享协议传输层
     * @param appid     已校验的应用 ID
     * @param notifyUrl 已校验的支付结果通知地址
     */
    WechatPayApplication(WechatPayTransport transport,
                         String appid,
                         URI notifyUrl) {
        this.appid = appid;
        this.notifyUrl = notifyUrl;
        nativePayClient = new NativePayClient(transport, appid, notifyUrl);
        jsapiPayClient = new JsapiPayClient(transport, appid, notifyUrl);
        h5PayClient = new H5PayClient(transport, appid, notifyUrl);
    }

    /**
     * 返回当前应用 ID。
     *
     * @return APPID
     */
    public String appid() {
        return appid;
    }

    /**
     * 返回当前应用固定的支付通知地址。
     *
     * @return 通知 URI
     */
    public URI notifyUrl() {
        return notifyUrl;
    }

    /**
     * 返回 Native 支付入口。
     *
     * @return Native 支付客户端
     */
    public NativePayClient nativePay() {
        return nativePayClient;
    }

    /**
     * 返回 JSAPI 支付入口，公众号网页与小程序共用。
     *
     * @return JSAPI 支付客户端
     */
    public JsapiPayClient jsapiPay() {
        return jsapiPayClient;
    }

    /**
     * 返回 H5 支付入口，用于微信外的手机浏览器。
     *
     * @return H5 支付客户端
     */
    public H5PayClient h5Pay() {
        return h5PayClient;
    }
}
