/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.jsapipay;

/**
 * 已验签的小程序支付交易创建结果。
 *
 * <p>小程序前端以 {@link #tradeNo()} 调用 {@code my.tradePay({ tradeNO })} 唤起收银台；
 * 交易创建成功不代表已付款，支付结果以异步通知或主动查单为准。</p>
 *
 * @param tradeNo    支付宝交易号
 * @param outTradeNo 商户订单号，已与请求核对一致
 */
public record JsapiPayResult(String tradeNo, String outTradeNo) {
}
