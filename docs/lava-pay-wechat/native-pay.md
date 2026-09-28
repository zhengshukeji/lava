# Native 支付

Native 支付由商户后端调用下单接口取得 `code_url`，再由前端把该 URL 渲染为二维码，用户使用微信扫一扫完成支付。

## 下单

```java
NativePrepayResponse response = application.nativePay().prepay(
        PrepayRequest.builder()
                .description("订单 ORDER_001")
                .outTradeNo("ORDER_001")
                .amount(100) // 单位：分
                .build()
);

URI codeUrl = response.codeUrl();
```

模块不引入二维码图片依赖。`codeUrl` 由前端或调用方选择二维码组件渲染。

微信支付返回的 `code_url` 有效期为 2 小时；过期后使用相同下单参数重新请求以取得新链接。

## 完整业务参数

```java
PrepayRequest request = PrepayRequest.builder()
        .description("深圳门店订单")
        .outTradeNo("ORDER_002")
        .amount(528_800)
        .detail(PrepayDetail.builder()
                .addGoodsDetail(
                        PrepayDetail.GoodsDetail.builder()
                                .merchantGoodsId("IPHONE_001")
                                .goodsName("iPhone")
                                .quantity(1)
                                .unitPrice(528_800)
                                .build()
                )
                .build())
        .sceneInfo(PrepaySceneInfo.builder()
                .payerClientIp("203.0.113.10")
                .deviceId("POS_001")
                .build())
        .profitSharing(false)
        .build();
```

所有公开金额使用 `long`，单位为分。`outTradeNo` 必须由商户生成并在业务中保持稳定。

## 支付结果

二维码展示和用户回到商户页面都不表示支付成功。稳定流程是：

1. 接收并验签支付成功通知；
2. 未及时收到通知时主动查单；
3. 使用后端可信订单核对 APPID、订单号和金额；
4. 在同一业务事务中幂等更新订单并保存微信支付订单号；
5. 超过业务有效期且查单仍为 `NOTPAY` 时再关单。

官方 Native 支付开发指引：[https://pay.weixin.qq.com/doc/v3/merchant/4012791891](https://pay.weixin.qq.com/doc/v3/merchant/4012791891)。
