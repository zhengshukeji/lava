# IO 与数据量

`ByteStreamUtils` 为流复制和读取增加明确的资源所有权与内存上限，避免无界读取把外部输入直接加载到堆内存。

## 资源所有权

| 传入方式 | 输入流 | 输出流 |
| --- | --- | --- |
| 直接传入 `InputStream` / `OutputStream` | 借用，不关闭 | 借用，不关闭、不刷新 |
| 传入 `InputStreamSource` | 每次打开并由 Lava 关闭 | 仍按目标参数决定 |
| 目标为 `Path` | 由 source 打开并关闭 | 由 Lava 打开并关闭 |

谁创建资源，谁负责关闭。调用方直接传入的流不会被模块擅自关闭。

## 有界读取

```java
// 第二个参数是字节数上限
byte[] bytes = ByteStreamUtils.readAllBytes(input, 2 * 1024 * 1024L);
String text = ByteStreamUtils.readUtf8(input, 256 * 1024L);
```

超过上限时抛出 `SizeLimitExceededException`：

```java
try {
    return ByteStreamUtils.readAllBytes(input, maximumBytes);
} catch (SizeLimitExceededException exception) {
    log.warn(
            "输入超过限制，maximumBytes={}, observedBytes={}",
            exception.maximumBytes(),
            exception.observedBytes()
    );
    throw exception;
}
```

不传上限的 `readAllBytes(...)` 默认最多读取 16 MiB。默认复制缓冲区为 8192 字节。

## 流复制

```java
long copied = ByteStreamUtils.copy(input, output);
long bounded = ByteStreamUtils.copyWithLimit(input, output, maximumBytes);
```

复制方法不会刷新借入的输出流。是否刷新、何时关闭由调用方决定。

## 可重复打开的输入源

`InputStreamSource` 适合重试读取、复制到文件，或把“如何打开流”延迟到实际执行时：

```java
InputStreamSource file = InputStreamSource.fromPath(sourcePath);
InputStreamSource bytes = InputStreamSource.fromBytes(payload);
InputStreamSource utf8 = InputStreamSource.fromString("内容");

long copied = ByteStreamUtils.copy(file, targetPath);
```

`fromBytes(...)` 会保护输入字节数组，后续修改原数组不会改变 source 的内容。

## 数据量格式化

```java
String iec = DataSizeFormatter.formatIec(1_048_576); // 1 MiB
String si = DataSizeFormatter.formatSi(1_000_000);   // 1 MB
```

- IEC 使用 1024 进制及 `KiB`、`MiB`、`GiB`；
- SI 使用 1000 进制及 `kB`、`MB`、`GB`；
- 不要把两种单位混用或用 `MB` 表示 1024 进制。
