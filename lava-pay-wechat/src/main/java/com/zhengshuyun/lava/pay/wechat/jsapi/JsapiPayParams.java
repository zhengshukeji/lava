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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 前端调起微信支付所需的已签名参数。
 *
 * <p>序列化为 JSON 后可直接传给公众号网页的 {@code WeixinJSBridge.invoke('getBrandWCPayRequest', ...)}
 * 或小程序的 {@code wx.requestPayment(...)}；小程序忽略 {@code appId} 字段。{@code package}
 * 是 Java 关键字，组件名为 {@code packageValue}，JSON 字段名仍为 {@code package}。</p>
 *
 * @param appId        调起支付的应用 ID，必须与下单 APPID 一致
 * @param timeStamp    Unix 秒时间戳文本
 * @param nonceStr     随机串
 * @param packageValue 订单详情扩展字符串，形如 {@code prepay_id=xxx}
 * @param signType     签名类型，固定为 {@code RSA}
 * @param paySign      商户私钥对前四个字段逐行拼接后的 Base64 签名
 */
public record JsapiPayParams(
        @JsonProperty("appId") String appId,
        @JsonProperty("timeStamp") String timeStamp,
        @JsonProperty("nonceStr") String nonceStr,
        @JsonProperty("package") String packageValue,
        @JsonProperty("signType") String signType,
        @JsonProperty("paySign") String paySign) {
}
