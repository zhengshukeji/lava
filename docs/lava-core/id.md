# ID 生成

`lava-core` 同时提供 UUIDv4、UUIDv7 和 Snowflake。三者解决的问题不同，不应只根据字符串长短选择。

## 选择建议

| 类型 | 特点 | 推荐用途 |
| --- | --- | --- |
| UUIDv4 | 随机、不可按生成时间排序 | 安全性要求更高、无需时间排序的公开标识 |
| UUIDv7 | RFC 9562、按时间排序、单实例严格单调 | 数据库主键、日志关联 ID、事件 ID |
| Snowflake | 64 位长整型、按时间排序、依赖唯一 worker | 内部高吞吐分布式主键 |

## UUIDv4

普通随机 UUID 直接通过 `IdUtils` 生成：

```java
// UUID 对象
UUID id = IdUtils.nextUUID();
// 含连字符文本，如 6c033286-c5dd-46f2-8523-42ba142ac81b
String text = IdUtils.nextUUIDString();
// 不含连字符文本，32 个十六进制字符
String compact = IdUtils.nextUUIDStringWithoutHyphens();
```

无连字符文本仍然表示同一个 128 位 UUID，只是展示形式不同。

## UUIDv7

需要按生成时间排序时使用 UUIDv7：

```java
// UUID 对象
UUID id = IdUtils.nextUUIDv7();
// 含连字符文本，如 01a0e959-d824-758d-84e5-d4afa562f693
String text = IdUtils.nextUUIDv7String();
// 不含连字符文本
String compact = IdUtils.nextUUIDv7StringWithoutHyphens();
```

`IdUtils` 复用进程内共享生成器。同一毫秒内或发生时钟回拨时，生成器递增 UUID 的 74 位随机区，因此单个实例内保持唯一且严格单调。

需要注入时钟或随机源进行测试时，可单独创建生成器：

```java
UUIDv7Generator generator = new UUIDv7Generator(clock, secureRandom);
UUID id = generator.next();
```

::: warning 边界
UUIDv7 的单调保证不跨进程共享。同一毫秒内的连续值具有可推导性，不能把 UUIDv7 当作访问令牌或防猜测凭据；这类场景使用 UUIDv4 或专门的安全随机令牌。
:::

## Snowflake

应用必须为每个并发运行实例分配不同的 `workerId`：

```java
// 37 是本实例的 workerId，部署时保证各实例不同
SnowflakeIdGenerator generator = IdUtils.newSnowflakeGenerator(37);

// 64 位长整型 ID
long id = generator.nextId();
// 字符串形态
String text = generator.nextIdString();
// 从 ID 反解出生成时刻
Instant createdAt = SnowflakeIdGenerator.timestamp(id);
```

生成器线程安全，应在进程内长期复用。当前布局如下：

| 区域 | 位数 | 说明 |
| --- | ---: | --- |
| 时间戳 | 41 | 相对 `2026-01-01T00:00:00Z` 的毫秒数 |
| worker | 10 | 范围 `0` 到 `1023` |
| 序列 | 12 | 每毫秒最多 4096 个序号 |

`workerId` 没有安全默认值。不要从主机名、Pod IP 或进程号截取、取模或哈希得到 worker ID，这些方案无法保证不碰撞。

Kubernetes StatefulSet 可以把稳定的 Pod 序号注入环境变量：

```yaml
env:
  - name: APP_SNOWFLAKE_WORKER_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.labels['apps.kubernetes.io/pod-index']
```

```java
int workerId = Integer.parseInt(System.getenv("APP_SNOWFLAKE_WORKER_ID"));
SnowflakeIdGenerator generator = IdUtils.newSnowflakeGenerator(workerId);
```

以下情况抛出 `IdGenerationException`，生成器不会隐藏等待：

- 当前时钟早于 Lava epoch；
- 时钟相对上次生成发生回拨；
- 同一毫秒内 4096 个序列已经耗尽；
- 时间超出 41 位时间戳可表示范围。
