# 回调通知

通知解析器与 Web 框架无关。调用方需要传入签名请求头和未经修改的原始请求正文。

## 构造请求头

```java
HttpHeaders headers = HttpHeaders.of(
        "Wechatpay-Serial",
        requestHeader("Wechatpay-Serial"),
        "Wechatpay-Signature",
        requestHeader("Wechatpay-Signature"),
        "Wechatpay-Timestamp",
        requestHeader("Wechatpay-Timestamp"),
        "Wechatpay-Nonce",
        requestHeader("Wechatpay-Nonce"),
        "Wechatpay-Signature-Type",
        requestHeader("Wechatpay-Signature-Type")
);
```

## 支付通知

```java
TransactionNotification notification = client.notifications()
        .parseTransaction(headers, rawRequestBody);

Order order = orderRepository.findByOutTradeNo(
        notification.transaction().outTradeNo()
);

notification.requireOrder(
        order.appid(),
        order.outTradeNo(),
        order.totalAmount()
);

orderService.markPaidIdempotently(
        notification.id(),
        notification.transaction().transactionId()
);
```

首次成功通知通常还没有本地微信支付订单号，可先使用 `requireOrder(...)` 核对下单前已有字段，并在同一事务中保存微信支付订单号和 OpenID。重复通知或主动查单时使用 `requirePaidOrder(...)` 完整核对。

## 退款通知

```java
RefundNotification notification = client.notifications()
        .parseRefund(headers, rawRequestBody);

notification.requireRefund(
        // 可信商户订单号
        "ORDER_001",
        // 可信商户退款单号
        "REFUND_001",
        // 可信原订单金额，单位为分
        100,
        // 可信退款金额，单位为分
        50
);
```

本地已保存微信侧订单号和退款单号时，使用六参数重载执行完整核对。

## 固定解析顺序

解析器依次执行：

1. 校验时间戳；
2. 使用微信支付公钥执行 RSA 验签；
3. 校验事件和资源类型；
4. 使用 APIv3 密钥执行 AES-GCM 解密；
5. 校验商户号；
6. 映射并校验业务模型。

微信支付 APIv3 的应答和回调都必须验签，回调资源再使用 APIv3 密钥解密。官方说明：[https://pay.weixin.qq.com/doc/v3/merchant/4012365342](https://pay.weixin.qq.com/doc/v3/merchant/4012365342)。

## 响应与幂等

- 验签失败必须返回非 2xx，不能特殊放行 `WECHATPAY/SIGNTEST/` 探测签名；
- 通知可能重复发送，业务必须按通知 ID 或业务单号幂等；
- 完成必要校验和可靠持久化后再返回成功；
- 原始正文不能先解析、重新序列化或修改后再交给验签器；
- 不要记录完整通知正文、密文和签名。

## 公钥切换边界

当前模块只配置一个微信支付公钥 ID，不支持同时加载平台证书和微信支付公钥。存量商户从平台证书迁移时，灰度期间可能收到平台证书签名的回调，模块会失败关闭。

应在公钥回调切换完成后使用本模块，或在迁移期保留能够根据 `Wechatpay-Serial` 选择证书/公钥的旧处理链路。官方迁移说明：[https://pay.weixin.qq.com/doc/v3/merchant/4012154180](https://pay.weixin.qq.com/doc/v3/merchant/4012154180)。
