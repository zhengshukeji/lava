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

覆盖简道云开放平台 API 文档列出的全部接口：

- 应用与表单：`applications()`、`forms()` — 应用列表、表单列表、表单字段
- 数据：`data()` — 单条/多条的查询、新建、修改、删除，`listAll` 按游标自动翻页；写入值自动包装 `{"value": ...}`
- 文件：`files()` — 获取上传凭证并上传附件/图片
- 流程：`workflows()` — 实例查询/结束/激活、流程日志、审批意见、待办处理（提交/否决/回退/转交/加签/撤回）、抄送
- 通讯录：`members()`、`departments()`、`roles()`、`roleGroups()`、`guests()`
- 资源用量与审计日志：`usage()`、`auditLogs()`
- 推送：`JiandaoyunWebhookUtils` — `X-JDY-Signature` 验签与事件解析

## 快速上手

```java
try (JiandaoyunClient client = JiandaoyunClient.builder()
        .apiKey(apiKey)
        .build()) {
    DataRecord record = client.data().create(appId, entryId, Map.of(
            "_widget_1", "张三",
            "_widget_2", 18));
    List<DataRecord> all = client.data().listAll(ListDataRequest.builder()
            .appId(appId).entryId(entryId).build());
}
```

失败语义：非 2xx 响应，或流程接口 2xx 下返回 `status=failure`，抛 `JiandaoyunApiException`（携带状态码、错误码和描述）；传输失败抛
`JiandaoyunTransportException`；响应无法按协议解释抛 `JiandaoyunProtocolException`。超频错误码
8303/8304 可等待 1 秒重试，本模块不做自动重试。

完整文档见 [docs/lava-jiandaoyun](../docs/lava-jiandaoyun/)。
