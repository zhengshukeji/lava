# 手机网站支付

手机网站支付对应 `alipay.trade.wap.pay`，用于手机浏览器中的 H5 页面：已安装支付宝时唤起客户端，否则进入支付宝 H5 收银台。与 [电脑网站支付](./page-pay) 一样，模块在商户服务端生成已签名页面跳转数据，不会代替浏览器请求支付宝。

## 创建支付入口

```java
var wapPay = client.wapPay(
        "https://pay.example.com/alipay/notify",
        "https://pay.example.com/alipay/return"
);
```

两个地址都会参与签名，必须是绝对 HTTP 或 HTTPS 地址。`WapPayClient` 可以复用来创建多笔订单。

## 创建订单参数

```java
WapPayRequest request = WapPayRequest.builder()
        .outTradeNo("ORDER_001")
        .totalAmount(10_000) // 单位：分，即 100.00 元
        .subject("订单 ORDER_001")
        .timeout(Duration.ofMinutes(30))
        .quitUrl("https://shop.example.com/order/ORDER_001")
        .build();
```

- `quitUrl` 可选，是用户付款中途退出时返回的商户页面；
- `product_code` 固定为 `QUICK_WAP_WAY`；
- 金额、有效期、渠道和商品明细（`GoodsDetail`）规则与电脑网站支付相同；
- 电脑网站支付的二维码选项在手机网站支付中不存在。

## POST 表单与 GET URL

```java
PayForm form = wapPay.createForm(request);   // 推荐：作为完整 HTML 响应输出
URI paymentUrl = wapPay.createUrl(request);  // 需要前端直接跳转时使用
```

`PayForm` 与电脑网站支付共用，输出方式和 GET URL 的安全要求见 [电脑网站支付](./page-pay#post-自动提交表单)。

## 同步返回不是支付结果

`return_url` 与 `quitUrl` 都只负责把用户带回商户页面。支付结果以已验签的 [异步通知](./notification) 或 [主动查单](./transaction) 为准。
