# 小程序支付

支付宝小程序支付使用 OpenAPI V3 `alipay.trade.create`，产品码固定为 `JSAPI_PAY`。商户后端创建交易取得支付宝交易号，小程序前端再用交易号唤起收银台。

## 创建交易

通知地址随请求体发送，绑定在 `JsapiPayClient` 上：

```java
JsapiPayResult result = client.jsapiPay("https://pay.example.com/alipay/notify")
        .create(JsapiPayRequest.builder()
                .outTradeNo("ORDER_001")
                .totalAmount(100) // 单位：分
                .subject("订单 ORDER_001")
                .buyerOpenId("小程序获取的买家 OpenID")
                .timeout(Duration.ofMinutes(15))
                .build());
```

小程序前端：

```js
my.tradePay({ tradeNO: tradeNoFromServer })
```

## 买家与应用

- `buyerOpenId` 与 `buyerId` 必须且只能配置一个：新应用使用 OpenID，`buyerId`（2088 开头）只适用于按 UID 配置的存量应用；
- 小程序与当前 OpenAPI 应用不是同一个时，通过 `opAppId(...)` 传入小程序 APPID；
- 金额、有效期、商品明细（`GoodsDetail`）和回传参数规则与 [电脑网站支付](./page-pay) 相同。

## 响应核对

模块会核对响应中的 `out_trade_no` 与请求一致，不一致时抛出 `AlipaySecurityException`；缺少 `trade_no` 时抛出 `AlipayProtocolException`。

## 支付结果

交易创建成功不代表已付款，`my.tradePay` 的回调也不能作为入账依据。支付结果以已验签的 [异步通知](./notification) 或 [主动查单](./transaction) 为准。
