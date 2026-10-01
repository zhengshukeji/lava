# 查单与关单

## 查询交易

```java
Transaction byMerchantOrder = client.transactions()
        .queryByOutTradeNo("ORDER_001");

Transaction byWechatOrder = client.transactions()
        .queryByTransactionId("4200000000000000001");
```

响应会先完成微信支付公钥验签，并核对请求和响应中的订单标识。

## 可信订单核对

首次成功通知或尚未保存微信侧标识时：

```java
transaction.requireOrder(
        // 可信 APPID
        "wx1234567890",
        // 可信商户订单号
        "ORDER_001",
        // 可信订单金额，单位为分
        100
);
```

已经保存微信支付订单号和付款人 OpenID 时执行完整核对：

```java
transaction.requirePaidOrder(
        // 可信 APPID
        "wx1234567890",
        // 可信商户订单号
        "ORDER_001",
        // 可信微信支付订单号
        "4200000000000000001",
        // 可信付款人 OpenID
        "openid-from-trusted-record",
        // 可信订单金额，单位为分
        100
);
```

验签只证明响应来自微信支付，不证明它就是当前业务订单。可信值必须来自商户后端记录，不能来自前端或当前响应。

## 交易状态

| 状态 | 含义 |
| --- | --- |
| `SUCCESS` | 支付成功 |
| `REFUND` | 转入退款 |
| `NOTPAY` | 未支付 |
| `CLOSED` | 已关闭 |
| `REVOKED` | 已撤销 |
| `USERPAYING` | 用户支付中 |
| `PAYERROR` | 支付失败 |

`transaction.paid()` 仅在状态为 `SUCCESS` 时返回 `true`。

## 关闭订单

```java
Transaction transaction = client.transactions()
        .queryByOutTradeNo("ORDER_001");

if (TradeState.NOTPAY.equals(transaction.tradeState())) {
    client.transactions().close("ORDER_001");
}
```

只关闭仍处于 `NOTPAY` 的订单。结果未知、未收到通知或准备让用户重新支付时，必须先查单，不能直接要求重复付款。
