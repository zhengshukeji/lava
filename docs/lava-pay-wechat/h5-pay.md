# H5 支付

H5 支付用于微信外的手机浏览器：商户后端下单取得 `h5_url`，前端跳转后拉起微信客户端完成支付。微信内打开的网页应使用 [JSAPI 支付](./jsapi-pay)。

使用前需在商户平台开通 H5 支付，并配置支付域名。

## 下单

H5 下单必须提供场景信息，其中 `payerClientIp` 和 `h5Info` 必填，缺失时在发送请求前抛出 `IllegalArgumentException`：

```java
H5PrepayResponse response = application.h5Pay().prepay(
        PrepayRequest.builder()
                .description("订单 ORDER_001")
                .outTradeNo("ORDER_001")
                .amount(100) // 单位：分
                .sceneInfo(PrepaySceneInfo.builder()
                        .payerClientIp("203.0.113.10") // 用户真实 IP，不是服务器 IP
                        .h5Info(PrepaySceneInfo.H5Info.of(H5Type.WAP))
                        .build())
                .build()
);

URI h5Url = response.h5Url();
```

场景类型取 `H5Type.WAP`（手机浏览器）、`H5Type.IOS` 或 `H5Type.ANDROID`；后两者需要时可用 `PrepaySceneInfo.H5Info.builder()` 补充应用名称、BundleID 或 PackageName。

## 跳转与返回

`h5_url` 有效期为 5 分钟。可在其后追加 URL 编码的 `redirect_url` 参数，指定支付后返回的商户页面。返回页面不代表支付成功，只能展示“正在确认支付结果”，再由服务端查单确认。
