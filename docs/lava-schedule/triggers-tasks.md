# 触发器与任务

## 一次性触发

```java
Trigger at = Trigger.at(instant);
Trigger after = Trigger.after(Duration.ofMinutes(5));
```

`after(...)` 允许零延迟。一次性触发完成后不会再产生下一次执行。

## 固定频率

```java
Trigger everyMinute = Trigger.fixedRate(Duration.ofMinutes(1));

Trigger delayed = Trigger.fixedRate(
        Duration.ofSeconds(10),
        Duration.ofMinutes(1)
);

Trigger anchored = Trigger.fixedRate(
        firstExecution,
        Duration.ofMinutes(1)
);
```

固定频率根据计划时间推进，不等待上一次任务完成。任务是否重叠、排队或跳过由并发策略决定。

## 固定延迟

```java
Trigger afterEachRun = Trigger.fixedDelay(Duration.ofMinutes(1));

Trigger delayed = Trigger.fixedDelay(
        Duration.ofSeconds(10),
        Duration.ofMinutes(1)
);
```

固定延迟在每次执行结束（含被跳过或被执行器拒绝）后再等待一个延迟，因此由触发器驱动的执行彼此不重叠。执行进行中时 `nextExecution()` 为 `null`。

## Cron

```java
Trigger utc = Trigger.cron("0 0 * * * ?");

Trigger shanghai = Trigger.cron(
        "0 0 9 * * ?",
        ZoneId.of("Asia/Shanghai")
);
```

不传时区时默认 UTC。涉及业务本地时间必须显式指定 `ZoneId`；夏令时行为由该时区和 Quartz 下一次触发计算共同决定。表达式在创建 Trigger 时立即校验。

## 注册和控制任务

```java
ScheduledTask task = scheduler.schedule(
        "billing-refresh",
        billingService::refresh,
        trigger,
        options
);

task.pause();
task.resume();
task.triggerNow();
task.reschedule(Trigger.cron("0 0/10 * * * ?"));

Instant next = task.nextExecution();
Instant previous = task.previousExecution();

boolean cancelled = task.cancel();
```

没有显式 ID 时，调度器生成 UUIDv7。任务 ID 在同一个调度器中必须唯一。`scheduler.tasks()` 返回当前全部任务的句柄快照。

`reschedule(...)` 原地替换触发器：正在进行的执行不受影响，且继续参与并发策略判断，不会与新触发器的执行重叠。不要用先 `cancel` 再重新 `schedule` 的方式修改触发规则，那样旧执行会被当作另一个任务，可能与新执行重叠。

`cancel()` 默认不打断正在执行的任务；`cancel(true)` 允许尝试中断。任务取消后，句柄仍可读取 ID，但不能继续暂停、恢复或立即触发。
