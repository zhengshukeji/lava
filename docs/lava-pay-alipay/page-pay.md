# 电脑网站支付

电脑网站支付对应 `alipay.trade.page.pay`。模块在商户服务端生成已签名页面跳转数据，不会代替浏览器请求支付宝。

## 创建支付入口

通知地址和同步返回地址会参与签名，并绑定在 `PagePayClient` 上：

```java
var pagePay = client.pagePay(
        "https://pay.example.com/alipay/notify",
        "https://pay.example.com/alipay/return"
);
```

两个地址都必须是绝对 HTTP 或 HTTPS 地址。`PagePayClient` 可以复用来创建多笔订单。

## 创建订单参数

```java
PagePayRequest request = PagePayRequest.builder()
        .outTradeNo("ORDER_001")
        .totalAmount(10_000) // 单位：分，即 100.00 元
        .subject("订单 ORDER_001")
        .body("订单商品说明")
        .timeout(Duration.ofMinutes(30))
        .build();
```

关键规则：

- 所有公开金额都使用 `long`，单位为分；
- `outTradeNo` 必须由商户生成并保持稳定；
- `timeExpire(...)` 和 `timeout(...)` 二选一；
- 有效期为 1 分钟到 15 天，`timeout` 必须是整分钟；
- 启用渠道和禁用渠道不能同时配置；
- `product_code` 固定为 `FAST_INSTANT_TRADE_PAY`；
- `integration_type` 固定为 `PCWEB`。

## POST 自动提交表单

支付宝建议优先使用 POST 模式：

```java
PayForm form = pagePay.createForm(request);

httpResponse.setContentType(PayForm.CONTENT_TYPE);
httpResponse.getWriter().write(form.html());
```

服务端应把 `form.html()` 作为完整 HTML 响应输出，浏览器加载后会自动提交。它是一段 HTML，不是 URL，不能赋给 `window.location.href`。

## GET 支付 URL

需要前端直接打开或重定向时，生成 GET URL：

```java
URI paymentUrl = pagePay.createUrl(request);
```

可以把 `paymentUrl` 返回给可信前端，再由浏览器跳转。该 URL 已包含 `biz_content`、回调地址和 RSA2 签名。

::: warning GET URL 是敏感数据
不要把完整 URL 写入应用日志、访问分析、监控标签或错误上报平台。支付宝页面跳转数据上限为 16384 个字符，超过时 `createUrl(...)` 抛出 `AlipayProtocolException`，应改用 POST 表单。
:::

## 商品和二维码选项

```java
PagePayRequest request = PagePayRequest.builder()
        .outTradeNo("ORDER_001")
        .totalAmount(10_000)
        .subject("订单 ORDER_001")
        .qrPayMode(PagePayQrMode.CUSTOM_WIDTH)
        .qrcodeWidth(220)
        .addGoodsDetail(GoodsDetail.builder()
                .goodsId("SKU_001")
                .goodsName("商品名称")
                .quantity(2)
                .price(5_000)
                .build())
        .build();
```

商品单价同样使用分。二维码宽度只在 `CUSTOM_WIDTH` 模式有效，范围为 1 到 9999。

## 同步返回不是支付结果

`return_url` 只负责把用户浏览器带回商户页面，可能被跳过、重复访问或伪造。同步返回页只能展示“正在确认支付结果”，随后由服务端查单；不能直接把本地订单更新为已支付。
