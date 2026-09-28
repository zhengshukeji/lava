# JSAPI 支付

JSAPI 支付用于公众号网页和小程序：商户后端下单取得 `prepay_id`，再用商户私钥生成调起支付参数，前端据此拉起微信收银台。

## 应用上下文

公众号和小程序的 APPID 不同，应各创建一个应用上下文；付款用户的 `openid` 必须是在同一 APPID 下获取的：

```java
WechatPayApplication miniProgram = client.application(
        "wx_mini_program_appid",
        "https://pay.example.com/wechat/transaction-notify"
);
```

## 下单并生成调起参数

```java
JsapiPayParams params = miniProgram.jsapiPay().prepayWithRequestPayment(
        "o-user-openid",
        PrepayRequest.builder()
                .description("订单 ORDER_001")
                .outTradeNo("ORDER_001")
                .amount(100) // 单位：分
                .build()
);
```

`PrepayRequest` 与 Native、H5 下单共用，可选的商品详情、场景信息和分账标记写法见 [Native 支付](./native-pay)。

`JsapiPayParams` 序列化后的 JSON 字段为 `appId`、`timeStamp`、`nonceStr`、`package`、`signType`、`paySign`：

- 小程序直接传给 `wx.requestPayment(...)`，`appId` 会被忽略；
- 公众号网页传给 `WeixinJSBridge.invoke('getBrandWCPayRequest', ...)`。

## 重新支付

`prepay_id` 有效期为 2 小时。用户取消后在有效期内重新支付时，只需对原 `prepay_id` 重新签名，不必再次下单：

```java
JsapiPrepayResponse prepay = miniProgram.jsapiPay().prepay("o-user-openid", request);
// 保存 prepay.prepayId()，之后可重复调用
JsapiPayParams params = miniProgram.jsapiPay().requestPayment(prepay.prepayId());
```

## 支付结果

前端 `requestPayment` 的成功回调只代表用户完成了操作，不能作为入账依据。支付结果以已验签的支付通知或主动查单为准，流程与 [Native 支付](./native-pay#支付结果) 相同。
