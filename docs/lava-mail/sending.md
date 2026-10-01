# 发送邮件

## 文本邮件

```java
SmtpServerConfig smtp = SmtpServerConfig.startTls(
        "smtp.example.com",
        587
);

MailCredential credential = new PasswordCredential(
        "robot@example.com",
        password
);

MailSendRequest request = MailSendRequest.text(
        // 发件人
        new MailAddress("robot@example.com", "Robot"),
        // 收件人
        List.of(new MailAddress("ops@example.com")),
        // 主题
        "Daily report",
        // 纯文本正文
        "All jobs completed."
);

try (MailSender sender = new MailSender(smtp, credential)) {
    MailSendResult result = sender.send(request);
}
```

`MailSendResult` 返回邮件 Message-ID 和发送时间。

## HTML 与附件

完整请求通过记录构造器创建：

```java
MailAttachment attachment = new MailAttachment(
        "report.csv",
        "text/csv",
        content
);

MailSendRequest request = new MailSendRequest(
        // 发件人
        new MailAddress("robot@example.com", "Robot"),
        // 主送
        List.of(new MailAddress("ops@example.com")),
        // 抄送
        List.of(),
        // 密送
        List.of(),
        // 回复地址
        List.of(),
        // 主题
        "Daily report",
        // 纯文本正文
        "请查看附件。",
        // HTML 正文
        "<p>请查看附件。</p>",
        // 附件
        List.of(attachment)
);
```

主送、抄送和密送至少有一项非空；纯文本正文和 HTML 正文至少提供一种。所有集合在构造时复制为不可变列表。

## TLS 模式

```java
// 明文连接后升级到 TLS，常用端口 587
SmtpServerConfig startTls = SmtpServerConfig.startTls(host, 587);
// 连接即 TLS，常用端口 465
SmtpServerConfig implicitTls = SmtpServerConfig.implicitTls(host, 465);
```

`SSL_TLS` 和 `STARTTLS` 都启用服务器身份校验。`STARTTLS` 必须升级成功，不会静默降级。

`PLAINTEXT` 只能通过完整构造器显式选择。生产凭证不得通过明文连接发送。
