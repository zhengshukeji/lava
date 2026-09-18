# Lava

Lava 是面向 Java 25 的模块化基础设施工具库，为 Java 应用提供 ID、重试、受限 IO、JSON、HTTP/SSE、支付渠道协议、进程内调度、密码学和邮件收发能力。
各模块可以独立使用，不依赖 Spring 或其他应用框架，适用于后端服务、命令行工具和独立 Java 应用。

- [GitHub](https://github.com/zhengshuyuncom/lava)
- [Issues](https://github.com/zhengshuyuncom/lava/issues)

## 模块

| 模块                                       | 主要能力                                                  |
|--------------------------------------------|-----------------------------------------------------------|
| [`lava-bom`](lava-bom/README.md)           | 统一管理全部 Lava 模块版本                                |
| [`lava-core`](lava-core/README.md)         | UUIDv7、Snowflake、重试、受限 IO、时间和参数校验          |
| [`lava-json`](lava-json/README.md)         | 基于 Jackson 3 的线程安全 JSON 编解码                     |
| [`lava-http`](lava-http/README.md)         | 基于 OkHttp 5 的 HTTP 客户端、流式响应和 SSE              |
| [`lava-pay-wechat`](lava-pay-wechat/README.md) | 微信支付 API v3 协议适配，不依赖微信官方 SDK          |
| [`lava-pay-alipay`](lava-pay-alipay/README.md) | 支付宝开放平台支付协议适配，不依赖支付宝官方 SDK      |
| [`lava-jiandaoyun`](lava-jiandaoyun/README.md)           | 简道云开放平台 API 适配，不依赖官方 SDK                  |
| [`lava-dingtalk`](lava-dingtalk/README.md) | 钉钉开放平台 API 适配，不依赖官方 SDK                    |
| [`lava-schedule`](lava-schedule/README.md) | 有界并发的进程内调度和 Cron 触发                          |
| [`lava-crypto`](lava-crypto/README.md)     | Argon2id、HMAC、RSA/AES、EC 密钥和 PEM 处理               |
| [`lava-mail`](lava-mail/README.md)         | SMTP、IMAP、MIME 和 OAuth 2 邮件认证                      |

## 快速开始

Lava 要求 JDK 25 或更高版本。推荐导入 `lava-bom` 统一管理版本，再按需声明模块：

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
    <dependency>
        <groupId>com.zhengshuyun</groupId>
        <artifactId>lava-http</artifactId>
    </dependency>
</dependencies>
```

`lava-bom` 只管理版本，不会自动引入任何模块。未使用 BOM 时，需要为每个 Lava 依赖显式指定相同版本。具体 API 和示例见各模块文档。

## 设计原则

- 默认使用不可变配置，可复用组件通过 Builder 创建。
- 调用方传入的流、执行器和客户端默认视为借用资源；由 Lava 创建的资源由 Lava 负责关闭。
- 使用 JSpecify 描述空值契约，未显式标注的参数和返回值默认不接受 `null`。
- 可预期的业务结果通过返回值表达，传输失败、资源越界和非法配置通过明确的异常类型表达。

## 许可证

[Apache License 2.0](LICENSE)
