# lava-schedule

`lava-schedule` 是实例级、纯进程内调度器，提供一次性、固定频率和 Cron 触发，以及有界并发和任务生命周期控制。

## 添加依赖

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-schedule</artifactId>
</dependency>
```

应用还需要自行选择 SLF4J 2 provider；模块生产依赖只包含 `slf4j-api`。

## 快速开始

```java
try (LavaScheduler scheduler = LavaScheduler.builder()
        .executionBounds(64, 256)
        .shutdownTimeout(Duration.ofSeconds(20))
        .listener(event -> metrics.record(event.status()))
        .build()) {
    ScheduledTask task = scheduler.schedule(
            "billing-refresh",
            billingService::refresh,
            Trigger.cron(
                    "0 0/5 * * * ?",
                    ZoneId.of("Asia/Shanghai")
            ),
            ConcurrencyPolicy.SKIP_IF_RUNNING
    );
}
```

## 明确边界

模块不提供：

- 数据库持久化；
- 进程重启恢复；
- 分布式锁；
- 集群唯一调度；
- 任务历史持久化。

Quartz 只用于校验 Cron 表达式和计算下一次触发时间。Lava 不创建 Quartz Scheduler，也不会把用户对象放入 `JobDataMap`。

需要 durable 或 cluster 调度时，应使用专门的调度系统，而不是依赖进程内任务。
