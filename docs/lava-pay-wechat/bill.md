# 账单

模块支持交易账单和资金账单申请，并负责安全下载、可选 GZIP 解压和 SHA-1 完整性校验。

## 交易账单

```java
BillDownloadInfo info = client.bills().applyTradeBill(
        TradeBillRequest.builder()
                .billDate(LocalDate.of(2026, 8, 28))
                .billType(TradeBillType.ALL)
                .build()
);
```

## 资金账单

```java
BillDownloadInfo info = client.bills().applyFundFlowBill(
        FundFlowBillRequest.builder()
                .billDate(LocalDate.of(2026, 8, 28))
                .accountType(FundFlowAccountType.BASIC)
                .build()
);
```

账单日期需早于当天且在微信支付允许的查询范围内（目前为最近三个月），范围由微信支付服务端校验。

## 下载

```java
BillDownloadResult result = client.bills().download(
        info,
        Path.of("/data/bills/2026-08-28.csv")
);
```

下载流程：

1. 在目标目录创建临时文件；
2. 流式下载，避免账单整体进入内存；
3. 请求 GZIP 时先解压；
4. 计算账单原文 SHA-1；
5. 与申请账单返回的摘要比较；
6. 校验成功后，通过同目录硬链接排他发布目标文件，再清理临时名称。

目标名称已被文件或符号链接占用时拒绝覆盖，包括下载期间其他写入方刚创建的目标。
目标文件系统必须支持硬链接；不支持时抛出 `WechatPayFileException`，失败分类为 `IO`，不会退化为覆盖移动或复制半文件。
模块不解析 CSV 字段，调用方仍需负责账单入库、对账和差异处理。

下载 URL 含临时 token，不得写入日志。摘要不一致时抛出 `WechatPaySecurityException`，临时文件不会作为成功账单发布。
