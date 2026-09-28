# lava-core

`lava-core` 提供不依赖通用第三方工具包的基础能力：RFC 9562 UUIDv7、显式 worker 的 Snowflake、实例化重试、有界流读取、IEC/SI
数据量格式化、严格时间格式、空值安全字符串与容器判断和实用校验。生产依赖只有 JSpecify。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-core</artifactId>
</dependency>
```

版本建议由 [`lava-bom`](../lava-bom/README.md) 管理。

## ID

普通 UUID 直接使用 JDK UUIDv4；需要按时间排序时显式选择 UUIDv7：

```java
UUID id = IdUtils.nextUUID();
String text = IdUtils.nextUUIDString();
String compactText = IdUtils.nextUUIDStringWithoutHyphens();
UUID orderedId = IdUtils.nextUUIDv7();
```

`nextUUIDv7()` 使用进程内共享生成器，在并发、同毫秒和时钟回拨时仍保持唯一且严格单调；需要可注入时间和熵源时，直接创建
`UUIDv7Generator(Clock, SecureRandom)`。这个保证不跨进程共享。

雪花算法要求由部署配置分配 `workerId`，生成器需在进程内复用：

```java
SnowflakeIdGenerator ids = IdUtils.newSnowflakeGenerator(37);
long id = ids.nextId();
Instant createdAt = SnowflakeIdGenerator.timestamp(id);
```

`workerId` 从哪里来由调用方决定——环境变量、系统属性或配置中心都可以。若 Kubernetes 集群为 StatefulSet Pod 提供
`apps.kubernetes.io/pod-index` 标签，其序号可作为唯一且重启后稳定的来源：

```yaml
env:
  - name: APP_SNOWFLAKE_WORKER_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.labels['apps.kubernetes.io/pod-index']
```

布局为 41 位时间、10 位 worker、12 位序列，Lava epoch 是 `2026-01-01T00:00:00Z`。`workerId` 范围是 0–1023；两个实例复用同一个
worker ID 会产出逐位相同的 ID 序列，因此这里没有默认值可用——`workerId` 无法由主机名、IP 或进程号可靠推导，10 位只有 1024
个取值，20 个实例哈希取值的碰撞概率已达 17%，而 Pod IP 的低位熵远小于 10 位。时钟回拨或单毫秒 4096 个序列耗尽时统一抛出
`IdGenerationException`，具体原因写入异常消息，生成器不会隐藏等待。

## 重试

策略不可变，执行器可以复用，并支持 checked operation、结果条件、异常条件、指数退避、full jitter 和监听器。

```java
RetryPolicy<String> policy = RetryPolicy.<String>builder()
        .maxAttempts(4)
        .exponentialBackoffWithFullJitter(
                Duration.ofMillis(100), 2.0, Duration.ofSeconds(2))
        .retryOnException(IOException.class)
        .retryOnResult(String::isBlank)
        .listener(attempt -> metrics.record(attempt.attempt(), attempt.willRetry()))
        .build();

String result = policy.call(service::load);
```

`maxAttempts` 包含第一次调用。最终 checked exception 会原样抛出；`InterruptedException`
不会被重试，并会恢复线程中断标志。只有幂等或具有明确去重语义的操作才应自动重试。

## IO 与所有权

```java
byte[] bytes = ByteStreamUtils.readAllBytes(input, 2 * 1024 * 1024L);
long copied = ByteStreamUtils.copy(InputStreamSource.fromPath(source), output);
```

- 传入原始 `InputStream`/`OutputStream` 时，Lava 借用它们：不关闭输出、不关闭借入输入，也不 flush 输出。
- 传入 `InputStreamSource` 时，Lava 拥有并关闭每次由 source 打开的输入流。
- 传入目标 `Path` 时，Lava 打开并关闭输出流。
- `readAllBytes` 默认上限为 16 MiB；超过上限抛 `SizeLimitExceededException`。

`DataSizeFormatter.formatIec` 使用 `KiB/MiB`（1024 进制），`formatSi` 使用 `kB/MB`（1000 进制），不会混用含义。

## 字符串

`StringUtils` 提供空值安全的常用判断和默认值转换：

```java
boolean missing = StringUtils.isBlank(input);
String name = StringUtils.defaultIfBlank(input, "anonymous");
String safe = StringUtils.nullToEmpty(input);
```

`isEmpty` 只识别 `null` 和空字符串，`isBlank` 还识别纯 Unicode 空白；转换和默认值方法不会裁剪或修改非空原值。

## 集合与映射

`CollectionUtils` 和 `MapUtils` 分别提供集合与映射的空值安全判断，`null` 与不包含元素或条目的容器均视为空：

```java
boolean noItems = CollectionUtils.isEmpty(items);
boolean hasHeaders = MapUtils.isNotEmpty(headers);
```

## 时间与校验

- `DateTimeFormatterUtils` 暴露不可变、严格解析的 `DateTimeFormatter`。
- `DurationFormatter` 只接受 `Duration` 能精确表达的天到纳秒，不把月或年近似成固定天数。
- `ValidationUtils` 提供参数条件、非空、非空白和非空集合校验。
