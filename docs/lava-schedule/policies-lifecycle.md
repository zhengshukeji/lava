# 并发、错过触发与生命周期

## 并发策略

| 策略 | 行为 |
| --- | --- |
| `ConcurrencyPolicy.SKIP_IF_RUNNING` | 默认。上一次仍在运行时跳过本次并产生 `SKIPPED` 事件，同一任务永不重叠 |
| `ConcurrencyPolicy.PARALLEL` | 允许同一任务的多次执行并行 |

全局并发由 `executionBounds(maxConcurrent, maxPending)` 约束：执行器满载且队列已满时产生 `REJECTED` 事件，不会创建无界虚拟线程。

## 错过触发

暂停后恢复、协调线程停顿或时钟前跳都可能让计划时刻被错过。错过的多次触发**合并为一次立即执行**，随后从当前时刻按触发器继续推进，积压再多也只执行一次。这与 Quartz 对 Cron 的默认处理（`FIRE_ONCE_NOW`）以及 `ScheduledThreadPoolExecutor` 迟到执行的行为一致；需要逐次补偿的场景应使用持久化调度系统。

## 任务事件

监听器接收四种终态：

- `SUCCESS`
- `FAILURE`
- `SKIPPED`
- `REJECTED`

```java
LavaScheduler scheduler = LavaScheduler.builder()
        .listener(event -> metrics.record(
                event.taskId(),
                event.status(),
                event.scheduledAt(),
                event.startedAt(),
                event.completedAt(),
                event.reason()
        ))
        .build();
```

监听器异常会被隔离，不中断调度器。

## 资源所有权

- 每个调度器始终拥有一个协调线程；
- 默认 worker 是调度器拥有的有界虚拟线程 executor；
- `.executor(executor)` 传入的执行器属于借用资源，不会被关闭；
- `close()` 停止新的 occurrence，并按配置超时等待活跃任务；
- 超时后会取消本调度器提交的任务，但不会关闭外部 executor；
- `close(Duration)` 返回是否在超时前完成全部活跃执行；
- 等待被中断时恢复中断标志并返回 `false`。

进程崩溃后，内存中的任务和执行事件都会丢失。
