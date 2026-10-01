# 限制、失败与安全

## 默认 MIME 限制

`MailLimits.DEFAULT`：

| 限制 | 默认值 |
| --- | ---: |
| 单个正文 | 10 MiB |
| 单个附件 | 25 MiB |
| 单次解码预算 | 50 MiB |
| MIME 嵌套深度 | 20 |

```java
MailLimits limits = new MailLimits(
        // 单个正文上限
        5 * MailLimits.MEBIBYTE,
        // 单个附件上限
        20 * MailLimits.MEBIBYTE,
        // 单次解码预算
        30 * MailLimits.MEBIBYTE,
        // MIME 嵌套深度
        15
);

MailClientOptions options = new MailClientOptions(
        limits,
        Clock.systemUTC(),
        // OAuth2 token 到期前提前刷新的时间
        Duration.ofMinutes(1)
);
```

发送时，单次预算覆盖正文和全部附件；读取正文时覆盖本次保留的 text/html；单独下载的每个附件同时受附件上限和单次预算限制。多个独立读取或下载调用不会累计同一个进程内预算。

## 失败分类

`MailException.kind()` 区分：

- `CONFIGURATION`
- `AUTHENTICATION`
- `TLS`
- `CONNECTION`
- `TIMEOUT`
- `PROTOCOL`
- `PARSING`
- `SIZE_LIMIT`

异常消息和 credential 的 `toString()` 不包含密码、refresh token 或 access token，但 credential accessor 返回的仍是真实秘密。

## 不可信内容

- `MailMessage.htmlBody()` 是未净化的远端 HTML，渲染前必须执行应用级净化；
- 附件文件名是不可信 metadata，落盘前必须生成安全文件名，阻止路径穿越和覆盖；
- Content-Type 不能代替文件内容检测；
- 不要把邮件正文、认证令牌或完整附件元数据直接写入日志；
- 发送或下载失败时优先写临时文件，完整成功后再发布目标文件。
