# 账单下载地址

模块通过 OpenAPI V3 查询已验签的临时下载信息，不负责实际下载、解压和解析账单文件。

## 日账单

```java
BillDownloadInfo info = client.bills().queryDaily(
        BillType.TRADE,
        LocalDate.now().minusDays(1)
);

URI downloadUrl = info.downloadUrl();
if (downloadUrl != null) {
    billDownloader.downloadImmediately(downloadUrl);
}
```

日账单日期需早于当天且在支付宝允许的查询范围内（目前为最近 6 年），范围由支付宝服务端校验。

## 月账单

```java
BillDownloadInfo info = client.bills().queryMonthly(
        BillType.TRADE,
        YearMonth.now().minusMonths(1)
);
```

月账单月份需早于当前月份且在支付宝允许的查询范围内，范围由支付宝服务端校验。

## 完整请求

直付通二级商户交易账单可指定 SMID：

```java
BillDownloadInfo info = client.bills().query(BillRequest.builder()
        .billType(BillType.TRADE_ZFT_MERCHANT)
        .date(LocalDate.now().minusDays(1))
        .smid(smid)
        .build());
```

`smid` 只允许用于 `TRADE_ZFT_MERCHANT`。日账单日期和月账单月份严格二选一；`SETTLEMENT_MERGE` 只支持日账单，且日期不能早于 2023-04-17。

## 下载与对账

支付宝账单下载地址具有很短的有效期，取得后应立即下载。`downloadUrl()` 可能为 `null`，此时通过 `fileCode()` 查看支付宝返回的文件状态。

调用方需要自行负责：

- 下载重试和结果确认；
- 文件落盘、权限与加密；
- 压缩包解压和账单字段解析；
- 本地订单、退款与账单的差异核对；
- 下载令牌和账单内容的日志脱敏。
