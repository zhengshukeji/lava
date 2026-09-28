# lava-schedule

`lava-schedule` 是实例级、纯进程内调度器。它不提供数据库持久化、进程重启恢复、分布式锁或集群调度。Quartz 只负责校验 Cron
表达式和计算下一次时间；Lava 不创建 Quartz Scheduler，也不把用户对象放入 `JobDataMap`。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-schedule</artifactId>
</dependency>
```

应用还应选择自己的 SLF4J 2 provider；本模块生产依赖只包含 `slf4j-api`。

## 使用

```java
try (LavaScheduler scheduler = LavaScheduler.builder()
        .executionBounds(64, 256)
        .shutdownTimeout(Duration.ofSeconds(20))
        .listener(event -> metrics.record(event.status()))
        .build()) {
    ScheduledTask task = scheduler.schedule(
            "billing-refresh",
            billingService::refresh,
            Trigger.cron("0 0/5 * * * ?", ZoneId.of("Asia/Shanghai")),
            ConcurrencyPolicy.SKIP_IF_RUNNING);

    // scheduler 的生命周期应覆盖任务需要运行的整个应用生命周期。
}
```

没有显式 ID 时，调度器生成 UUIDv7。`ScheduledTask` 可用于 `pause`、`resume`、`triggerNow`、查询前后执行时间和取消。

## Trigger

- `Trigger.at(Instant)`：绝对时间执行一次。
- `Trigger.after(Duration)`：相对延迟后执行一次。
- `Trigger.fixedRate(...)`：固定 rate，以预定时间而非任务完成时间推进。
- `Trigger.cron(expression)`：Cron，默认 UTC。
- `Trigger.cron(expression, zoneId)`：显式时区 Cron，构造时立即校验表达式。

涉及业务本地时间时必须显式传 `ZoneId`。Cron 的 DST 行为由该时区和 Quartz 的下一次触发计算决定。

## 并发与错过触发

默认 `ConcurrencyPolicy.SKIP_IF_RUNNING`：上一次仍在运行时跳过本次并产生 `SKIPPED` 事件；`PARALLEL` 允许同一任务并行。
全局并发由 `executionBounds` 约束，执行器满载时产生 `REJECTED` 事件，不创建无界虚拟线程。

错过的多次触发合并为一次立即执行，随后从当前时刻继续推进，与 Quartz 对 Cron 的默认 misfire 处理一致。

`TaskEventListener` 接收 `SUCCESS`、`FAILURE`、`SKIPPED`、`REJECTED` 终态事件和时间戳。监听器异常会被隔离，不中断调度器。

## 所有权和关闭

- `LavaScheduler` 始终拥有一个协调线程。
- 默认 worker 是调度器拥有的有界虚拟线程 executor。
- `builder.executor(executor)` 传入的 executor 是 borrowed；调度器绝不调用其 `shutdown`。
- `close()` 停止新的 occurrence，并等待活跃任务至配置超时；超时后会取消本调度器提交的任务，但不会关闭外部 executor。
- `close(Duration)` 返回是否在超时前结束全部活跃执行。等待被中断时会恢复中断标志并返回 `false`。

进程崩溃时内存中的任务和执行事件都会丢失；需要 durable/cluster 调度时应选择专用系统。
