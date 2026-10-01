# 生命周期、失败与安全

## 客户端所有权

| 创建方式 | `close()` 行为 |
| --- | --- |
| `HttpClient.builder().build()` | 取消本实例调用，并关闭 dispatcher、连接池和 cache |
| `OkHttpInterop.borrowed(okHttpClient)` | 只取消包装器创建的调用，不关闭共享资源 |
| `OkHttpInterop.owned(okHttpClient)` | 接管并关闭外部客户端资源 |

关闭后的同步、流式和 SSE 请求都会被拒绝。长生命周期应用通常创建并复用少量客户端，不要每个请求新建一个连接池。

## 客户端配置

```java
HttpClient client = HttpClient.builder()
        .baseUrl("https://api.example.com/")
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(10))
        .callTimeout(Duration.ofSeconds(60))
        // 最大空闲连接 10 个，空闲连接存活 5 分钟
        .connectionPool(10, Duration.ofMinutes(5))
        .followRedirects(true)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build();
```

支付、扣款和其他非幂等协议通常应关闭自动重试和重定向，由业务显式处理结果未知状态。

## 失败分类

`HttpException.getKind()` 区分：

- `DNS`
- `CONNECTION`
- `TLS`
- `TIMEOUT`
- `CANCELLED`
- `PROTOCOL`
- `IO`
- `RESPONSE_TOO_LARGE`

`HttpStatusException` 表示调用方显式执行 `requireSuccess()` 后遇到非成功状态码。

## 脱敏边界

异常 URL、metadata、header 和 `toString()` 会脱敏认证头、Cookie 及常见 token/secret 查询参数。但以下内容仍可能包含秘密：

- 响应正文；
- SSE 失败响应正文；
- 业务自定义的罕见敏感参数；
- 自定义拦截器记录的原始请求。

不要直接记录完整请求或响应对象。只记录必要的状态码、失败分类、脱敏 URL、请求 ID 和耗时。

## OkHttp 高级入口

代理、Cookie、拦截器、特殊 TLS 或原生请求体通过 `OkHttpInterop` 接入。它是明确的逃生口；一旦使用，调用方负责线程安全、日志脱敏、重试语义和资源所有权。
