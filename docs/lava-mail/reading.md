# 读取邮件

## 分页列表

```java
ImapServerConfig imap = ImapServerConfig.implicitTls(
        "imap.example.com",
        993
);

try (MailReader reader = new MailReader(imap, credential)) {
    MailQuery query = MailQuery.firstPage(50);
    MailPage<MailMessageSummary> page = reader.listMessages(query);

    for (MailMessageSummary summary : page.items()) {
        MailMessage message = reader.readMessage(summary.id());
    }

    if (page.nextCursor() != null) {
        MailPage<MailMessageSummary> older = reader.listMessages(
                query.nextPage(page.nextCursor())
        );
    }
}
```

列表只返回 header、状态和附件元数据，不保留正文或附件字节。

## 查询条件

`MailQuery.firstPage(pageSize)` 使用默认文件夹且不带过滤条件。完整记录可配置：

- 文件夹；
- 每页 1 到 1000 条；
- 是否只查未读；
- 收件时间上下界；
- 发件人包含文本；
- 主题包含文本。

下一页必须在原查询上调用 `nextPage(cursor)`，确保过滤条件保持一致。

## 稳定标识

`MailMessageId(folder, uidValidity, uid)` 是后续读取正文和附件的稳定标识。邮箱 UIDVALIDITY 改变后，旧消息 ID 和游标会被拒绝，调用方应从第一页重新同步。

每次 reader 操作独立打开并关闭 IMAP store 和 folder，不在调用之间持有远端会话状态。

## 附件下载

```java
try (OutputStream output = Files.newOutputStream(target)) {
    // attachmentIndex 是摘要列表中附件元数据的下标
    long bytes = reader.downloadAttachment(
            messageId,
            attachmentIndex,
            output
    );
}
```

`downloadAttachment(...)` 借用输出流，不关闭也不刷新。调用方负责关闭输出流，并处理下载失败后的临时文件。
