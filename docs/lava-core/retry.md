# 重试

不可变且线程安全的 `RetryPolicy` 既定义“是否重试、等待多久、如何观测”，也直接执行操作：有返回值用 `call(...)`，无返回值用 `run(...)`。

## 基本用法

```java
RetryPolicy<String> policy = RetryPolicy.<String>builder()
        // 最多执行 4 次（含首次调用）
        .maxAttempts(4)
        // 每次失败后固定等待 200 毫秒
        .fixedDelay(Duration.ofMillis(200))
        // 只有 IOException 触发重试
        .retryOnException(IOException.class)
        // 结果为空白字符串时同样重试
        .retryOnResult(String::isBlank)
        .build();

// 执行操作并按策略自动重试
String result = policy.call(service::load);
```

`maxAttempts` 包含第一次调用。上例最多执行 4 次，而不是第一次加 4 次重试。

## 延迟策略

构建器提供三种常用策略：

```java
// 固定延迟：每次重试前固定等 200 毫秒
RetryPolicy<String> fixed = RetryPolicy.<String>builder()
        .fixedDelay(Duration.ofMillis(200))
        .build();

// 指数退避：100ms 起，每次 ×2，封顶 2s
RetryPolicy<String> exponential = RetryPolicy.<String>builder()
        .exponentialBackoff(
                Duration.ofMillis(100),
                2.0,
                Duration.ofSeconds(2)
        )
        .build();

// 指数退避 + 完全抖动：在 [0, 当前退避值) 内随机取延迟
RetryPolicy<String> jitter = RetryPolicy.<String>builder()
        .exponentialBackoffWithFullJitter(
                Duration.ofMillis(100),
                2.0,
                Duration.ofSeconds(2)
        )
        .build();
```

大量调用方可能同时失败时，优先使用完全抖动，避免所有实例按同一节奏重试。也可以通过 `delay(RetryDelayStrategy)` 注入自定义策略。

## 异常与结果条件

```java
RetryPolicy<Response> policy = RetryPolicy.<Response>builder()
        // 按异常判断：IO 类失败才重试
        .retryOnException(exception -> exception instanceof IOException)
        // 按结果判断：503 视为可重试
        .retryOnResult(response -> response.statusCode() == 503)
        .build();
```

默认行为是：

- 最多尝试 3 次；
- 所有 `Exception` 都可重试；
- 正常返回的结果不重试；
- 重试前不等待。

生产代码通常应显式收窄异常条件。最终受检异常会原样抛出，不会包装成另一种异常。

## 监听每次尝试

```java
RetryPolicy<String> policy = RetryPolicy.<String>builder()
        // 每次尝试结束后回调，可读取尝试序号、是否将继续重试、下次延迟和失败原因
        .listener(attempt -> metrics.record(
                attempt.attempt(),
                attempt.maxAttempts(),
                attempt.willRetry(),
                attempt.nextDelay(),
                attempt.failure()
        ))
        .build();
```

监听器在每次尝试结束后调用。`RetryAttempt` 同时包含结果和异常字段，未发生的一侧为 `null`。

## 无返回值操作

```java
RetryPolicy<Void> policy = RetryPolicy.<Void>builder()
        // 最多执行 3 次（含首次调用）
        .maxAttempts(3)
        // 只有 IOException 触发重试
        .retryOnException(IOException.class)
        .build();

// 无返回值版本，语义同 call(...)
policy.run(service::refresh);
```

`run(...)` 只根据异常重试，不使用结果条件。

## 中断与幂等

`InterruptedException` 永远不会被重试，并且会恢复当前线程的中断标记。等待阶段被中断时也遵循相同规则。

::: danger 不要重试非幂等操作
创建订单、扣款、发放权益等操作只有在请求带稳定幂等键，或远端协议明确保证重复调用安全时才能自动重试。网络异常往往表示“结果未知”，不等于远端没有执行。
:::
