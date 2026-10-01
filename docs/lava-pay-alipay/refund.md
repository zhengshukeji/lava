# 退款与退款查询

每笔退款必须使用稳定且唯一的 `outRequestNo`。它既是商户退款标识，也是网络结果未知时安全重试和查询的依据。

## 发起退款

```java
RefundRequest request = RefundRequest.builder()
        .outTradeNo("ORDER_001")
        .outRequestNo("REFUND_001")
        .refundAmount(5_000) // 单位：分
        .reason("用户取消")
        .build();

RefundResult result = client.refunds().apply(request);
if (result.succeeded()) {
    refundService.markSucceededIdempotently("REFUND_001");
}
```

可以使用商户订单号或支付宝交易号定位原交易；两者同时提供时，支付宝优先使用交易号。退款金额和商品退款金额都使用分。

`RefundResult.succeeded()` 仅在支付宝明确返回 `fund_change=Y` 时为 `true`。HTTP 请求成功不等于资金已经明确变化。

## 部分商品退款

```java
RefundRequest request = RefundRequest.builder()
        .outTradeNo("ORDER_001")
        .outRequestNo("REFUND_001")
        .refundAmount(5_000)
        .addGoodsDetail(RefundGoodsDetail.builder()
                .goodsId("SKU_001")
                .refundAmount(5_000)
                .outItemId("ITEM_001")
                .outSkuId("SKU_001")
                .build())
        .build();
```

商品明细总退款金额应与本次退款金额保持业务一致，调用方还应在发起前检查累计退款额不能超过原订单金额。

## 查询退款

```java
RefundQueryResult latest = client.refunds().query(
        RefundQueryRequest.builder()
                .outTradeNo("ORDER_001")
                .outRequestNo("REFUND_001")
                .build()
);

latest.requireRefund(
        // 可信商户订单号
        "ORDER_001",
        // 可信商户退款请求号
        "REFUND_001",
        // 可信原订单金额，单位为分
        10_000,
        // 可信退款金额，单位为分
        5_000
);

if (latest.succeeded()) {
    refundService.markSucceededIdempotently("REFUND_001");
}
```

`succeeded()` 只在退款状态为 `REFUND_SUCCESS` 时返回 `true`。默认查询选项包含银行卡冲退信息。

## 结果未知时

DNS、连接、TLS、超时或非预期 HTTP 状态都可能表示结果未知。此时：

1. 保持原 `outRequestNo` 和原退款金额不变；
2. 优先调用退款查询确认结果；
3. 如业务决定重试，仍使用完全相同的退款请求号和金额；
4. 不要创建新的退款请求号，否则可能发生重复退款。

银行卡原路退款的最终入账状态可结合 [银行卡冲退完成通知](./notification#银行卡冲退完成通知) 处理。
