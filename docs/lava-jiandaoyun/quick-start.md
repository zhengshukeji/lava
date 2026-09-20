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

客户端线程安全，建议应用生命周期内共享一个实例。`close()` 会清除 API Key 引用并关闭自建的 HTTP 连接资源。

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

## 处理失败

```java
try {
    client.applications().list(ListApplicationsRequest.builder().build());
} catch (JiandaoyunApiException exception) {
    // 简道云返回业务错误：状态码、错误码和描述走访问器获取
    log.warn("jiandaoyun api error: status={}, code={}",
            exception.statusCode(), exception.code());
}
```

超频错误码 8303/8304 可等待 1 秒重试；模块不做自动重试。
