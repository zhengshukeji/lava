# 字符串、集合与校验

## 字符串处理

`StringUtils` 提供空值安全的常用字符串操作：

```java
boolean empty = StringUtils.isEmpty(input);
boolean blank = StringUtils.isBlank(input);
String safe = StringUtils.nullToEmpty(input);
String name = StringUtils.defaultIfBlank(input, "anonymous");
```

| 方法 | `null` | `""` | `"  "` |
| --- | ---: | ---: | ---: |
| `isEmpty` | `true` | `true` | `false` |
| `isBlank` | `true` | `true` | `true` |
| `defaultIfEmpty(value, "x")` | `"x"` | `"x"` | 保留原值 |
| `defaultIfBlank(value, "x")` | `"x"` | `"x"` | `"x"` |

完整方法包括 `isEmpty`、`isNotEmpty`、`isBlank`、`isNotBlank`、`nullToEmpty`、`emptyToNull`、
`defaultIfEmpty` 和 `defaultIfBlank`。这些方法不执行 `trim` 或 `strip`，非空原值会保持不变。

## 集合与映射判断

`CollectionUtils` 与 `MapUtils` 分别处理 `Collection` 和 `Map`，避免重复编写 null 与空容器判断：

```java
if (CollectionUtils.isEmpty(items)) {
    return;
}

if (MapUtils.isNotEmpty(headers)) {
    send(headers);
}
```

两者都把 `null` 视为空；`isNotEmpty` 仅在容器不为 `null` 且至少包含一个元素或条目时返回 true。

## 参数校验

`ValidationUtils` 适合在构造器和公开方法入口表达调用契约：

```java
ValidationUtils.requireTrue(pageSize > 0, "pageSize must be positive");
ValidationUtils.requireFalse(items.isEmpty(), "items must not be empty");

String name = ValidationUtils.requireNotBlank(input, "name must not be blank");
List<Item> items = ValidationUtils.requireNotEmpty(values, "items must not be empty");
Map<String, String> headers = ValidationUtils.requireNotEmpty(
        values,
        "headers must not be empty"
);
```

校验失败统一抛出 `IllegalArgumentException`；方法返回经过校验的原值，便于直接赋给字段。
