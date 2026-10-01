# Mapper 配置

需要自定义日期格式、区域或 Jackson 模块时，显式创建独立 mapper：

```java
ObjectMapper mapper = JsonMapperFactory.builder()
        .locale(Locale.SIMPLIFIED_CHINESE)
        .zone(ZoneId.of("Asia/Shanghai"))
        .localDateTimePattern("uuuu-MM-dd HH:mm:ss")
        .localDatePattern("uuuu-MM-dd")
        .localTimePattern("HH:mm:ss")
        .build();

JsonCodec json = new JsonCodec(mapper);
```

字段上的 `@JsonFormat` 优先于 mapper 的默认格式。

## Long 全字符串模式

只有显式注册 `LongAsStringModule` 时，所有 long 形态才编码为字符串：

```java
JsonCodec json = new JsonCodec(JsonMapperFactory.builder()
        .addModule(new LongAsStringModule())
        .build());
```

规则覆盖：

- `long` 和 `Long`；
- `long[]`；
- 集合和嵌套对象；
- `OptionalLong`。

单个字段可以恢复为 number：

```java
record Payload(
        // 注解显式恢复为 number
        @JsonFormat(shape = JsonFormat.Shape.NUMBER) long numericId,
        // 默认编码为 string
        long stringId
) {
}
```

模块不会根据 JavaScript 安全整数范围动态切换 JSON 类型，避免同一字段有时是 number、有时是 string。

## Jackson 逃生口

```java
ObjectMapper mapper = JsonMapperFactory.builder()
        .customize(builder -> {
            // 配置工厂未直接建模的 Jackson 能力。
        })
        .build();

ObjectMapper raw = json.mapper();
```

`customize(...)` 和 `mapper()` 会暴露底层 Jackson 能力。启用多态类型、宽松反序列化或自定义模块时，调用方必须自行评估输入安全和兼容性。
