# lava-schedule

`lava-schedule` 是实例级、纯进程内调度器，提供一次性、固定频率和 Cron 触发，以及有界并发和任务生命周期控制。

## 添加依赖

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-schedule</artifactId>
</dependency>
```

版本推荐由 [lava-bom](../lava-bom/) 管理。应用还需要自行选择 SLF4J 2 provider；模块生产依赖只包含 `slf4j-api`。

## 能力概览

| 能力 | 入口 | 要点 |
| --- | --- | --- |
| 一次性触发 | `Trigger.at(...)`、`Trigger.after(...)` | 完成后不再触发 |
| 固定频率 | `Trigger.fixedRate(...)` | 按计划时刻推进，是否重叠由并发策略决定 |
| 固定延迟 | `Trigger.fixedDelay(...)` | 上一次结束后再计时，执行彼此不重叠 |
| Cron | `Trigger.cron(...)` | Quartz 表达式；不传时区默认 UTC |
| 并发策略 | `ConcurrencyPolicy` | 默认 `SKIP_IF_RUNNING`，同一任务不重叠 |
| 错过触发 | `MisfirePolicy` | 默认合并为一次立即执行 |
| 任务生命周期 | `ScheduledTask` | 暂停、恢复、原地改期、取消 |
| 执行事件 | `Builder.listener(...)` | `SUCCESS` / `FAILURE` / `SKIPPED` / `REJECTED` |

## 快速开始

```java
try (LavaScheduler scheduler = LavaScheduler.create()) {
    ScheduledTask task = scheduler.schedule(
            "billing-refresh",
            billingService::refresh,
            // 每 5 分钟；业务时区不传默认 UTC
            Trigger.cron("0 0/5 * * * ?", ZoneId.of("Asia/Shanghai"))
    );
}
```

并发策略默认 `SKIP_IF_RUNNING`，任务在调度器自建的有界虚拟线程池上执行，零配置即可起步。触发器的完整用法见[触发器与任务](./triggers-tasks)；并发上限、错过触发与生命周期见[并发、Misfire 与生命周期](./policies-lifecycle)。

## 明确边界

模块不提供：

- 数据库持久化；
- 进程重启恢复；
- 分布式锁；
- 集群唯一调度；
- 任务历史持久化。

Quartz 只用于校验 Cron 表达式和计算下一次触发时间。Lava 不创建 Quartz Scheduler，也不会把用户对象放入 `JobDataMap`。

需要 durable 或 cluster 调度时，应使用专门的调度系统，而不是依赖进程内任务。
