# lava-jiandaoyun

`lava-jiandaoyun` 是简道云开放平台的 API 工具包，基于 `lava-http` 与 `lava-json` 直接对接开放接口，
不依赖简道云官方 SDK，与 Lava 其他模块保持一致的客户端与错误处理风格。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-jiandaoyun</artifactId>
</dependency>
```

## 已实现能力

- 应用查询：`client.applications().list(...)` — API Key 授权范围内的应用列表（`/api/v5/app/list`）
- 表单查询：`client.forms().list(...)` — 指定应用下的表单列表（`/api/v5/app/entry/list`）
- 表单字段：`client.forms().listWidgets(appId, entryId)` — 字段结构、系统字段和数据修改时间（`/api/v5/app/entry/widget/list`）

## 快速上手

```java
try (JiandaoyunClient client = JiandaoyunClient.builder()
        .apiKey(apiKey)
        .build()) {
    List<Form> forms = client.forms().list(ListFormsRequest.builder()
            .appId("5e0dca0cc9a2790006c11e02")
            .build());
}
```

失败语义：非 2xx 响应抛 `JiandaoyunApiException`（携带状态码、错误码和描述）；传输失败抛
`JiandaoyunTransportException`；响应无法按协议解释抛 `JiandaoyunProtocolException`。超频错误码
8303/8304 可等待 1 秒重试，本模块不做自动重试。

完整文档见 [docs/lava-jiandaoyun](../docs/lava-jiandaoyun/)。
