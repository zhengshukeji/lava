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

## 更新时区分省略字段与清空字段

单条 `update` 和批量 `batchUpdate` 都只发送 `data` Map 中存在的字段，不自动补齐其他字段：

| Map 内容 | SDK 发送行为 |
| --- | --- |
| 不包含该字段的 key | 请求中省略该字段，表达不修改的意图 |
| 包含 key，值为 `null` | 发送 `{"value":null}`，表达清空的意图 |
| 包含 key，值为 `""` 或空列表 | 原样发送空文本或空列表，不视为省略 |
| 包含 key，值为非空值 | 发送指定值 |

不要将整个 DTO 连同未赋值属性直接转为更新 Map；未赋值属性若变成 null 条目，就会发送清空意图。
批量更新会把相同内容发送给所有目标记录。应显式选择待修改字段：

```java
Map<String, Object> changes = new LinkedHashMap<>();
changes.put("_widget_2", 19);
// 只有业务明确要求清空时才放入 null；Map.of 不支持 null 值
changes.put("_widget_remark", null);
// 不修改的字段不要放入 changes
client.data().update(
        appId,
        entryId,
        dataId,
        changes
);
```

子表单不能按普通字段做行级增量更新。当前请求约定要求整体提交子表单，保留的行需带原 `_id`，
不要只提交一行就假定其他行会被保留。[官方修改接口说明](https://hc.finecloud.com/app/open/14224)
明确指出，未传或传错子表单行 ID 会替换原子表单数据并生成新行 ID。

### 上线前的测试表验收

本地模拟测试只能验证 SDK 发送内容，不能证明简道云服务端的数据保留规则。
普通字段省略/null 的逐项语义尚未通过真实环境验收，不应将模拟测试通过视为生产验收完成。
请在独立测试表中写入有值的测试记录，每次更新后重新查询，至少核对：

1. 仅修改一个普通字段，其他未传字段的原值保持不变。
2. 显式传 null、空文本、空列表时，各对应字段类型的实际结果符合业务意图。
3. 批量更新多条测试记录时，未传字段保持原值，清空只影响明确指定的字段。
4. 子表单保留正确行 ID、遗漏行、遗漏行内字段、空列表等情况的实际结果；分别使用独立测试记录。

记录请求、更新前后数据和表单配置；启用智能助手的业务还需单独验证其联动效果。不要用生产记录进行上述实验。

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
