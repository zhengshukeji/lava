# 快速开始

## 添加依赖

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-jiandaoyun</artifactId>
</dependency>
```

版本推荐由 [lava-bom](../lava-bom/) 管理。

## 准备 API Key

在简道云「开放平台 &gt;&gt; 密钥管理 &gt;&gt; API Key」创建密钥，并按需配置应用授权范围、接口授权范围和 IP 白名单。API Key 即模块唯一的接入凭证。

## 创建根客户端

```java
try (JiandaoyunClient client = JiandaoyunClient.builder()
        .apiKey(apiKey)
        .build()) {
    // 通过根客户端获取领域入口
}
```

客户端线程安全，建议应用生命周期内共享一个实例。`close()` 会关闭自建的 HTTP 连接资源，调用方借入的 HTTP 客户端保持可用；关闭后再获取任何领域入口都会抛出 `IllegalStateException`。

## 查询应用列表

```java
List<Application> applications = client.applications()
        .list(ListApplicationsRequest.builder()
                .limit(50)
                .skip(100)
                .build());
```

未配置 `limit`/`skip` 时省略对应字段，由简道云应用默认值（100 条、跳过 0 条）。

## 查询表单列表

```java
List<Form> forms = client.forms().list(ListFormsRequest.builder()
        .appId("5e0dca0cc9a2790006c11e02")
        .limit(50)
        .build());
```

## 查询表单字段

```java
FormWidgets widgets = client.forms().listWidgets(appId, entryId);

for (FormWidgets.Widget widget : widgets.widgets()) {
    // widget.name() 是字段别名（无别名时为字段标识），数据接口按它存取字段值
    // widget.widgetName() 是控件 ID，两者不要混淆
}
```

子表单控件的子字段位于 `widget.items()`；系统字段（`flowState`、`createTime` 等）位于 `widgets.sysWidgets()`；`dataModifyTime()` 返回表单内数据最新修改时间（ISO-8601 UTC）。

## 查询数据

```java
// 查询单条
DataRecord record = client.data().get(appId, entryId, dataId);
String name = (String) record.value("_widget_1");
Member creator = record.creator();

// 按条件查询一页（按数据 ID 升序）
List<DataRecord> page = client.data().list(ListDataRequest.builder()
        .appId(appId).entryId(entryId)
        .filter(DataFilter.and(
                DataCondition.eq("_widget_1", "张三"),
                DataCondition.range("_widget_2", 10, 20)))
        .limit(100)
        .build());

// 取全部数据：按 data_id 游标自动翻页
List<DataRecord> all = client.data().listAll(ListDataRequest.builder()
        .appId(appId).entryId(entryId).build());

// 转为自定义 POJO（字段上用 @JsonProperty("_widget_xxx") 标注）
Person person = record.as(Person.class);
```

## 新建、修改、删除数据

值直接写，SDK 会自动包成 `{"value": ...}`：

```java
DataRecord created = client.data().create(appId, entryId, Map.of(
        "_widget_1", "张三",
        "_widget_2", 18,
        "_widget_3", List.of("选项1", "选项2"),
        // 子表单：行列表，每行同样是 字段名 -> 值；保留原行时带上行 _id
        "_widget_4", List.of(Map.of("_id", rowId, "_widget_41", "李四"))));

client.data().update(appId, entryId, created.id(), Map.of("_widget_2", 19));
client.data().delete(appId, entryId, created.id());
```

需要发起流程、触发智能助手或指定提交人时使用请求对象：

```java
client.data().create(CreateDataRequest.builder()
        .appId(appId).entryId(entryId)
        .data(Map.of("_widget_1", "张三"))
        .dataCreator("xiaoyun")
        .startWorkflow(true)
        .startTrigger(true)
        .build());
```

批量接口：`batchCreate`、`batchUpdate`（同一组值写入多条）、`batchDelete`，单次最多 100 条。

## 上传附件

```java
String transactionId = UUID.randomUUID().toString();
String key = client.files().upload(appId, entryId, transactionId, Path.of("合同.pdf"));

client.data().create(CreateDataRequest.builder()
        .appId(appId).entryId(entryId)
        .data(Map.of("_widget_attachment", List.of(key)))
        .transactionId(transactionId)   // 必须与获取上传凭证时相同
        .build());
```

一次上传多个文件时，先 `getUploadTokens(...)` 取一批凭证（每个凭证只能用一次），再逐个 `upload(token, file)`。

## 处理流程待办

```java
WorkflowTaskPage tasks = client.workflows().listTasks(ListWorkflowTasksRequest.builder()
        .username("xiaoyun")
        .build());

for (WorkflowTask task : tasks.tasks()) {
    client.workflows().approveTask("xiaoyun", task.instanceId(), task.taskId(), "同意");
}

WorkflowInstance instance = client.workflows().getInstance(instanceId, true);
```

否决、回退、转交、加签、撤回分别使用 `rejectTask`、`rollbackTask`、`transferTask`、`addSign`、`revokeTask`。操作失败（如流程已结束）抛 `JiandaoyunApiException`。

## 通讯录

```java
Member member = client.members().get("xiaoyun");
List<Department> departments = client.departments().list(1, true);   // 根部门编号为 1
List<Member> deptMembers = client.departments().listMembers(1, true);
client.roles().addMembers(roleNo, List.of("xiaoyun"));
```

## 接收推送（Webhook）

推送 URL 的查询参数带 `nonce` 与 `timestamp`，请求头 `X-JDY-Signature` 带签名。必须用**原始请求正文**验签：

```java
if (!JiandaoyunWebhookUtils.verify(secret, nonce, timestamp, rawBody, signature)) {
    return ResponseEntity.status(401).build();
}
WebhookEvent event = JiandaoyunWebhookUtils.parse(rawBody);
if (event.isDataEvent()) {
    DataRecord record = event.asDataRecord();
}
```

## 处理失败

```java
try {
    client.applications().list(ListApplicationsRequest.builder().build());
} catch (JiandaoyunApiException exception) {
    // 简道云返回业务错误（含流程接口 2xx + status=failure）：状态码、错误码和描述走访问器获取
    log.warn("jiandaoyun api error: status={}, code={}",
            exception.statusCode(), exception.code());
}
```

超频错误码 8303/8304 可等待 1 秒重试；模块不做自动重试。
