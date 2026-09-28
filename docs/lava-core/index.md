# lava-core

`lava-core` 是 Lava 的零框架基础模块，提供 ID、重试、有界流读取、字符串与容器处理、时间格式化和参数校验能力。它不依赖 Spring，生产依赖只有 JSpecify，适合在服务端应用、命令行工具和独立 Java 程序中直接使用。

## 环境要求

- JDK 25 或更高版本
- Maven 3.9 或更高版本

推荐通过 `lava-bom` 统一管理版本：

```xml
<properties>
    <lava.version>x.y.z</lava.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.zhengshuyun</groupId>
            <artifactId>lava-bom</artifactId>
            <version>${lava.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.zhengshuyun</groupId>
        <artifactId>lava-core</artifactId>
    </dependency>
</dependencies>
```

不使用 BOM 时，需要在 `lava-core` 依赖中显式填写版本。

## 能力概览

| 能力 | 主要入口 | 适用场景 |
| --- | --- | --- |
| ID | `IdUtils`、`UUIDv7Generator`、`SnowflakeIdGenerator` | UUIDv4、时间有序 UUIDv7、分布式长整型 ID |
| 重试 | `RetryPolicy`、`RetryDelayStrategy` | 受控重试、指数退避、完全抖动、尝试观测 |
| IO | `ByteStreamUtils`、`InputStreamSource` | 有界读取、流复制、明确资源所有权 |
| 字符串 | `StringUtils` | 空值安全判断、空值转换和默认值 |
| 集合 | `CollectionUtils`、`MapUtils` | 集合与映射的空值安全判断 |
| 时间 | `DateTimeFormatterUtils`、`TimeUtils`、`DurationFormatter` | 严格日期格式、兼容解析、时长展示 |
| 校验 | `ValidationUtils` | 参数、非空白文本、非空集合与 Map 校验 |

## 设计边界

- 未显式标注为可空的参数和返回值默认非空。
- 调用方传入的流属于借用资源，模块不会擅自关闭或刷新。
- 自动重试只适用于幂等操作，或已经具备业务去重语义的操作。
- UUIDv7 的单调性只在单个生成器实例内成立；Snowflake 的唯一性依赖部署侧正确分配 `workerId`。

接下来可从 [ID 生成](./id) 或 [重试](./retry) 开始。
