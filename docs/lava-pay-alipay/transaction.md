# 查单与关单

交易查询和关闭使用 OpenAPI V3 REST 请求。所有成功响应都会先验签，再核对请求与响应中的交易标识。

## 按商户订单号查询

```java
Trade trade = client.transactions().queryByOutTradeNo("ORDER_001");

trade.requireOrder(
        // 可信商户订单号
        "ORDER_001",
        // 可信订单金额，单位为分
        10_000
);
if (trade.paid()) {
    orderService.markPaidIdempotently(trade.tradeNo());
}
```

`requireOrder(...)` 使用后端可信订单号和金额再次核对结果，防止已验签但属于其他订单的响应被业务误用。

也可以按支付宝交易号查询：

```java
Trade trade = client.transactions().queryByTradeNo(alipayTradeNo);
```

需要扩展查询字段时使用 `TradeQueryRequest`：

```java
Trade trade = client.transactions().query(TradeQueryRequest.builder()
        .outTradeNo("ORDER_001")
        .addQueryOption(TradeQueryOption.FUND_BILL_LIST)
        .build());
```

## 交易状态

| 状态 | 含义 | `paid()` |
| --- | --- | --- |
| `WAIT_BUYER_PAY` | 等待买家付款 | `false` |
| `TRADE_CLOSED` | 未付款关闭，或支付后全额退款 | `false` |
| `TRADE_SUCCESS` | 支付成功，仍可退款 | `true` |
| `TRADE_FINISHED` | 交易结束，不可退款 | `true` |

## 何时查单

以下场景必须先查单：

- 页面支付请求结果未知；
- 没有及时收到异步通知；
- 通知处理失败后进行补偿；
- 准备用新订单号要求用户重新支付；
- 传输异常后无法确定支付宝是否处理过请求。

没有确认旧订单结果前，不应要求用户再次付款。

## 关闭待支付交易

```java
Trade trade = client.transactions().queryByOutTradeNo("ORDER_001");
if (TradeState.WAIT_BUYER_PAY.equals(trade.tradeState())) {
    TradeCloseResult result = client.transactions()
            .closeByOutTradeNo("ORDER_001");
}
```

带操作员编号时使用完整请求：

```java
TradeCloseResult result = client.transactions().close(
        TradeCloseRequest.builder()
                .outTradeNo("ORDER_001")
                .operatorId("OPERATOR_001")
                .build()
);
```

只关闭 `WAIT_BUYER_PAY` 交易。已付款或已经关闭的交易会由支付宝返回业务错误，模块不会伪造成功结果。
