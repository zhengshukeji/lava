# 退款与退款查询

## 申请退款

```java
RefundRequest request = RefundRequest.builder()
        .outTradeNo("ORDER_001")
        .outRefundNo("REFUND_001")
        .amount(50, 100) // 本次退款、原订单金额，单位均为分
        .reason("用户取消")
        .notifyUrl(URI.create(
                "https://pay.example.com/wechat/refund-notify"
        ))
        .build();

Refund refund = client.refunds().apply(request);
```

原交易可以使用 `outTradeNo` 或 `transactionId` 定位。每笔退款必须使用稳定且唯一的 `outRefundNo`。

## 查询退款

```java
Refund latest = client.refunds()
        .queryByOutRefundNo("REFUND_001");

latest.requireRefund(
        // 可信商户订单号
        "ORDER_001",
        // 可信微信支付订单号
        "4200000000000000001",
        // 可信商户退款单号
        "REFUND_001",
        // 可信微信支付退款单号
        "5000000000000000001",
        // 可信原订单金额，单位为分
        100,
        // 可信退款金额，单位为分
        50
);
```

完整核对包括商户订单号、微信支付订单号、商户退款单号、微信支付退款单号、原订单金额和退款金额。

## 退款状态

| 状态 | 含义 |
| --- | --- |
| `SUCCESS` | 退款成功 |
| `CLOSED` | 退款关闭 |
| `PROCESSING` | 退款处理中 |
| `ABNORMAL` | 退款异常 |

退款申请成功只表示微信支付已受理，最终结果应结合退款通知和退款查询确认。

## 结果未知

网络超时、连接失败或 TLS 异常后：

1. 不要更换 `outRefundNo`；
2. 使用相同金额和参数显式重试，或查询原退款单；
3. 不要把传输异常直接当作退款失败；
4. 不要创建新的退款单号，否则可能重复退款。
