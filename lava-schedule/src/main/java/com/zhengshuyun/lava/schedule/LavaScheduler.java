/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhengshuyun.lava.schedule;

import com.zhengshuyun.lava.core.id.IdUtils;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实例级、纯进程内的任务调度器。
 *
 * <p>每个实例拥有一个协调线程和默认的有界虚拟线程执行器。通过
 * {@link Builder#executor(ExecutorService)} 提供的执行器属于借入资源，调度器不会关闭它。
 */
public final class LavaScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LavaScheduler.class);
    /**
     * 晚于计划时间超过该宽限即视为错过触发；错过的多次触发合并为一次立即执行。
     */
    private static final Duration MISFIRE_GRACE = Duration.ofMillis(10);

    private final Clock clock;
    private final ScheduledThreadPoolExecutor coordinator;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final Duration shutdownTimeout;
    private final TaskEventListener listener;
    private final Map<String, TaskControl> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private final ThreadLocal<Integer> executionDepth = ThreadLocal.withInitial(() -> 0);
    private final Object lifecycleLock = new Object();
    private final Object terminationMonitor = new Object();

    private LavaScheduler(Builder builder) {
        this.clock = builder.clock;
        this.shutdownTimeout = builder.shutdownTimeout;
        this.listener = builder.listener;
        ThreadFactory coordinatorFactory = Thread.ofPlatform()
                .daemon(true)
                .name(builder.threadNamePrefix + "-coordinator", 0)
                .factory();
        this.coordinator = new ScheduledThreadPoolExecutor(1, coordinatorFactory);
        this.coordinator.setRemoveOnCancelPolicy(true);
        this.coordinator.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.coordinator.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        if (builder.executor == null) {
            this.executor = newOwnedExecutor(
                    builder.maxConcurrentExecutions,
                    builder.maxPendingExecutions,
                    builder.threadNamePrefix);
            this.ownsExecutor = true;
        } else {
            this.executor = builder.executor;
            this.ownsExecutor = false;
        }
    }

    /**
     * 使用默认配置创建调度器。
     *
     * @return 新的调度器
     */
    public static LavaScheduler create() {
        return builder().build();
    }

    /**
     * 创建调度器构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用 RFC 9562 UUIDv7 标识注册任务，重叠执行时跳过本次。
     *
     * @param task    待执行的任务
     * @param trigger 决定执行时间的触发器
     * @return 已注册任务的生命周期句柄
     */
    public ScheduledTask schedule(Runnable task, Trigger trigger) {
        return schedule(IdUtils.nextUUIDv7String(), task, trigger, ConcurrencyPolicy.SKIP_IF_RUNNING);
    }

    /**
     * 使用指定标识注册任务，重叠执行时跳过本次。
     *
     * @param id      全局唯一且非空白的任务标识
     * @param task    待执行的任务
     * @param trigger 决定执行时间的触发器
     * @return 已注册任务的生命周期句柄
     */
    public ScheduledTask schedule(String id, Runnable task, Trigger trigger) {
        return schedule(id, task, trigger, ConcurrencyPolicy.SKIP_IF_RUNNING);
    }

    /**
     * 使用指定标识和并发策略注册任务。
     *
     * <p>错过触发（暂停后恢复、协调线程停顿或时钟前跳）时，错过的多次触发合并为一次立即执行，
     * 随后从当前时刻继续按触发器推进，与 Quartz 对 Cron 的默认 misfire 处理一致。
     *
     * @param id                全局唯一且非空白的任务标识
     * @param task              待执行的任务
     * @param trigger           决定执行时间的触发器
     * @param concurrencyPolicy 上一次执行尚未结束时的处理方式
     * @return 已注册任务的生命周期句柄
     */
    public ScheduledTask schedule(
            String id, Runnable task, Trigger trigger, ConcurrencyPolicy concurrencyPolicy) {
        String taskId = requireNotBlank(id, "id");
        ValidationUtils.requireNonNull(task, "task must not be null");
        ValidationUtils.requireNonNull(trigger, "trigger must not be null");
        ValidationUtils.requireNonNull(concurrencyPolicy, "concurrencyPolicy must not be null");

        TaskControl control = new TaskControl(taskId, task, trigger, concurrencyPolicy);
        synchronized (lifecycleLock) {
            ensureOpen();
            TaskControl existing = tasks.putIfAbsent(taskId, control);
            if (existing != null) {
                throw new ScheduleException("A task with this id already exists: " + taskId);
            }
            try {
                control.start();
                return new ScheduledTask(control);
            } catch (RuntimeException e) {
                tasks.remove(taskId, control);
                control.deactivate(false);
                throw e;
            }
        }
    }

    /**
     * 判断指定标识的任务是否仍在调度器中。
     *
     * @param id 非空白的任务标识
     * @return 存在时为 true
     */
    public boolean hasTask(String id) {
        return tasks.containsKey(requireNotBlank(id, "id"));
    }

    /**
     * 查找指定标识的任务句柄。
     *
     * @param id 非空白的任务标识
     * @return 任务句柄；任务不存在时为 null
     */
    public @Nullable ScheduledTask getTask(String id) {
        TaskControl control = tasks.get(requireNotBlank(id, "id"));
        return control == null ? null : new ScheduledTask(control);
    }

    /**
     * 取消指定任务，但不打断正在执行的任务。
     *
     * @param id 非空白的任务标识
     * @return 成功取消时为 true
     */
    public boolean cancel(String id) {
        TaskControl control = tasks.get(requireNotBlank(id, "id"));
        return control != null && control.cancel(false);
    }

    /**
     * 判断调度器是否已经关闭。
     *
     * @return 已关闭时为 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 使用构建器配置的超时时间关闭调度器。
     */
    @Override
    public void close() {
        close(shutdownTimeout);
    }

    /**
     * 停止新的 occurrence，并等待正在执行的任务；借入的执行器不会被关闭。
     *
     * @param timeout 等待正在执行任务结束的最长时间
     * @return 若所有其他活动执行在超时前结束则为 true。若从任务或其终态监听器中调用，
     * 调用者自身的执行必须等本方法返回后才能结束，因此会从等待计数中排除。
     */
    public boolean close(Duration timeout) {
        requireNonNegative(timeout, "timeout");
        int callerExecutionDepth = currentExecutionDepth();
        List<TaskControl> controls;
        boolean firstClose;
        synchronized (lifecycleLock) {
            firstClose = closed.compareAndSet(false, true);
            controls = firstClose ? List.copyOf(tasks.values()) : List.of();
            if (firstClose) {
                tasks.clear();
                coordinator.shutdownNow();
                if (ownsExecutor) {
                    executor.shutdown();
                }
            }
        }
        if (firstClose) {
            for (TaskControl control : controls) {
                control.deactivate(false);
            }
        }

        boolean terminated = awaitActiveExecutions(timeout, callerExecutionDepth);
        if (!terminated) {
            Thread excludedThread = callerExecutionDepth == 0 ? null : Thread.currentThread();
            for (TaskControl control : controls) {
                control.cancelExecutions(true, excludedThread);
            }
            // 从调度器自有执行器的任务中调用 close() 时，shutdownNow() 会打断调用者。
            // 已跟踪的 FutureTask 足以打断其他执行；此处保留 shutdown()，让调用者正常返回。
            if (ownsExecutor && callerExecutionDepth == 0) {
                executor.shutdownNow();
            }
        }
        return terminated;
    }

    private boolean awaitActiveExecutions(Duration timeout, int callerExecutionDepth) {
        long remaining;
        try {
            remaining = timeout.toNanos();
        } catch (ArithmeticException e) {
            remaining = Long.MAX_VALUE;
        }
        long deadline = System.nanoTime() + remaining;
        synchronized (terminationMonitor) {
            while (activeExecutions.get() > callerExecutionDepth) {
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(terminationMonitor, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remaining = deadline - System.nanoTime();
            }
        }
        return true;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Scheduler is closed");
        }
    }

    private void emit(TaskEvent event) {
        try {
            listener.onEvent(event);
        } catch (Throwable listenerFailure) {
            log.warn("Scheduled task listener failed, taskId={}", event.taskId(), listenerFailure);
        }
    }

    private void executionReserved() {
        activeExecutions.incrementAndGet();
    }

    private void executionReleased() {
        activeExecutions.decrementAndGet();
        // 在执行中调用 close() 时，等待目标是当前执行深度而不是零，因此每次释放都可能唤醒等待者。
        synchronized (terminationMonitor) {
            terminationMonitor.notifyAll();
        }
    }

    private void enterExecution() {
        executionDepth.set(currentExecutionDepth() + 1);
    }

    private void exitExecution() {
        int depth = currentExecutionDepth();
        if (depth <= 1) {
            executionDepth.remove();
        } else {
            executionDepth.set(depth - 1);
        }
    }

    private int currentExecutionDepth() {
        return executionDepth.get();
    }

    private static ExecutorService newOwnedExecutor(
            int maximumConcurrency, int maximumPending, String namePrefix) {
        ThreadFactory factory = Thread.ofVirtual().name(namePrefix + "-worker", 0).factory();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                maximumConcurrency,
                maximumConcurrency,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(maximumPending),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * 可变的任务状态；所有复合状态转换都由 {@link #lock} 保护。
     */
    final class TaskControl {

        private final String id;
        private final Runnable task;
        private final Trigger trigger;
        private final ConcurrencyPolicy concurrencyPolicy;
        private final Object lock = new Object();
        private final Set<Execution> executions = new HashSet<>();

        private boolean paused;
        private boolean cancelled;
        private int running;
        private @Nullable Instant nextExecution;
        private @Nullable Instant previousExecution;
        private @Nullable ScheduledFuture<?> wakeup;

        TaskControl(String id, Runnable task, Trigger trigger, ConcurrencyPolicy concurrencyPolicy) {
            this.id = id;
            this.task = task;
            this.trigger = trigger;
            this.concurrencyPolicy = concurrencyPolicy;
        }

        String id() {
            return id;
        }

        void start() {
            Instant first = trigger.firstFireTime(clock.instant());
            synchronized (lock) {
                nextExecution = first;
            }
            if (first == null) {
                return;
            }
            try {
                // 初始 misfire 也交给协调线程处理，避免监听器在 schedule() 持有生命周期锁时重入。
                coordinator.execute(this::resolveMisfiresAndSchedule);
            } catch (RejectedExecutionException e) {
                throw new ScheduleException("Coordinator rejected task: " + id, e);
            }
        }

        void pause() {
            synchronized (lock) {
                requireActive();
                paused = true;
                if (wakeup != null) {
                    wakeup.cancel(false);
                    wakeup = null;
                }
            }
        }

        void resume() {
            synchronized (lock) {
                requireActive();
                if (!paused) {
                    return;
                }
                paused = false;
            }
            resolveMisfiresAndSchedule();
        }

        boolean isPaused() {
            synchronized (lock) {
                return paused;
            }
        }

        boolean cancel(boolean mayInterruptIfRunning) {
            boolean changed = deactivate(mayInterruptIfRunning);
            if (changed) {
                tasks.remove(id, this);
            }
            return changed;
        }

        boolean deactivate(boolean mayInterruptIfRunning) {
            synchronized (lock) {
                if (cancelled) {
                    return false;
                }
                cancelled = true;
                if (wakeup != null) {
                    wakeup.cancel(false);
                    wakeup = null;
                }
            }
            // 始终取消已提交但尚未开始的执行；只有调用者明确要求时才打断运行中的执行。
            cancelExecutions(mayInterruptIfRunning, null);
            return true;
        }

        void cancelExecutions(
                boolean mayInterruptIfRunning, @Nullable Thread excludedRunningThread) {
            List<Execution> snapshot;
            synchronized (lock) {
                snapshot = List.copyOf(executions);
            }
            for (Execution execution : snapshot) {
                execution.cancel(mayInterruptIfRunning, excludedRunningThread);
            }
        }

        boolean exists() {
            synchronized (lock) {
                return tasks.get(id) == this && !cancelled;
            }
        }

        void triggerNow() {
            synchronized (lock) {
                requireActive();
            }
            offerOccurrence(clock.instant());
        }

        @Nullable Instant nextExecution() {
            synchronized (lock) {
                return nextExecution;
            }
        }

        @Nullable Instant previousExecution() {
            synchronized (lock) {
                return previousExecution;
            }
        }

        /**
         * 安排下一次唤醒；若计划时刻已错过，则把错过的触发合并为一次立即执行，
         * 再从当前时刻推进到下一个未来触发时刻。
         */
        private void resolveMisfiresAndSchedule() {
            while (true) {
                Instant scheduledAt;
                Instant now = clock.instant();
                synchronized (lock) {
                    if (cancelled || paused || nextExecution == null) {
                        return;
                    }
                    scheduledAt = nextExecution;
                    Duration lateness = Duration.between(scheduledAt, now);
                    if (lateness.isNegative() || lateness.compareTo(MISFIRE_GRACE) <= 0) {
                        scheduleWakeupLocked(scheduledAt, now);
                        return;
                    }
                    // 直接从当前时刻计算下一次，跳过其间全部错过的触发，避免遍历长时间积压
                    nextExecution = trigger.nextFireTime(now);
                }
                // 监听器属于用户代码，在状态锁之外派发
                offerOccurrence(scheduledAt);
            }
        }

        private void scheduleWakeupLocked(Instant scheduledAt, Instant now) {
            long delayNanos;
            try {
                delayNanos = Math.max(0L, Duration.between(now, scheduledAt).toNanos());
            } catch (ArithmeticException e) {
                delayNanos = Long.MAX_VALUE;
            }
            try {
                wakeup = coordinator.schedule(
                        () -> onDue(scheduledAt), delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException e) {
                if (!closed.get()) {
                    throw new ScheduleException("Coordinator rejected task: " + id, e);
                }
            }
        }

        private void onDue(Instant scheduledAt) {
            Instant now = clock.instant();
            boolean misfired;
            synchronized (lock) {
                wakeup = null;
                if (cancelled || paused || !Objects.equals(nextExecution, scheduledAt)) {
                    return;
                }
                if (now.isBefore(scheduledAt)) {
                    // 墙上时钟回拨后，相对延迟可能提前结束；此时重新安排本次执行，避免任务提前运行。
                    scheduleWakeupLocked(scheduledAt, now);
                    return;
                }
                if (Duration.between(scheduledAt, now).compareTo(MISFIRE_GRACE) > 0) {
                    // 协调线程停顿或时钟前跳导致逾期，交给统一的错过触发处理；
                    // 处理过程会调用监听器，因此在状态锁外继续
                    misfired = true;
                } else {
                    previousExecution = scheduledAt;
                    nextExecution = trigger.nextFireTime(scheduledAt);
                    misfired = false;
                }
            }
            if (misfired) {
                resolveMisfiresAndSchedule();
                return;
            }
            offerOccurrence(scheduledAt);
            Instant next;
            synchronized (lock) {
                next = nextExecution;
                if (cancelled || paused || next == null) {
                    return;
                }
                scheduleWakeupLocked(next, clock.instant());
            }
        }

        private void offerOccurrence(Instant scheduledAt) {
            TaskEventStatus immediateStatus;
            String reason;
            synchronized (lock) {
                if (cancelled || closed.get()) {
                    immediateStatus = TaskEventStatus.REJECTED;
                    reason = "scheduler closed or task cancelled";
                } else if (running > 0 && concurrencyPolicy == ConcurrencyPolicy.SKIP_IF_RUNNING) {
                    immediateStatus = TaskEventStatus.SKIPPED;
                    reason = "previous execution is still active";
                } else {
                    // 全局并发上限由执行器的有界队列保证，超出时执行器拒绝并产生 REJECTED
                    running++;
                    executionReserved();
                    immediateStatus = null;
                    reason = null;
                }
            }
            if (immediateStatus == null) {
                dispatchReserved(scheduledAt);
            } else {
                emitTerminal(immediateStatus, scheduledAt, null, reason);
            }
        }

        private void dispatchReserved(Instant scheduledAt) {
            Execution execution = new Execution(scheduledAt);
            boolean rejected;
            synchronized (lock) {
                rejected = cancelled || closed.get();
                if (!rejected) {
                    executions.add(execution);
                }
            }
            if (rejected) {
                releaseUnstartedReservation(execution.state);
                // 监听器属于用户代码，必须在任务状态锁之外调用
                emitTerminal(TaskEventStatus.REJECTED, scheduledAt, null,
                        "scheduler closed or task cancelled");
                return;
            }
            try {
                executor.execute(execution.future);
            } catch (RejectedExecutionException e) {
                synchronized (lock) {
                    executions.remove(execution);
                }
                releaseUnstartedReservation(execution.state);
                emitTerminal(TaskEventStatus.REJECTED, scheduledAt, null, "execution executor rejected task");
            }
        }

        private void executeOccurrence(Instant scheduledAt) {
            Instant startedAt = clock.instant();
            @Nullable Throwable failure = null;
            try {
                task.run();
            } catch (Throwable thrown) {
                failure = thrown;
            } finally {
                Instant completedAt = clock.instant();
                if (failure == null) {
                    emit(new TaskEvent(
                            id,
                            TaskEventStatus.SUCCESS,
                            scheduledAt,
                            startedAt,
                            completedAt,
                            null,
                            null));
                } else {
                    emit(new TaskEvent(
                            id,
                            TaskEventStatus.FAILURE,
                            scheduledAt,
                            startedAt,
                            completedAt,
                            failure,
                            null));
                }
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

        /**
         * 执行状态 0=未开始、1=运行中、2=已释放；CAS 保证每次预留只释放一次。
         */
        private void releaseUnstartedReservation(AtomicInteger executionState) {
            if (executionState.compareAndSet(0, 2)) {
                releaseReservationAccounting();
            }
        }

        private void releaseStartedReservation(AtomicInteger executionState) {
            if (executionState.compareAndSet(1, 2)) {
                releaseReservationAccounting();
            }
        }

        private void releaseReservationAccounting() {
            synchronized (lock) {
                running--;
            }
            executionReleased();
        }

        /**
         * 提交到共享执行器、并显式维护开始状态的一次 occurrence。
         */
        private final class Execution {

            private final AtomicInteger state = new AtomicInteger();
            private final FutureTask<Void> future;
            private volatile @Nullable Thread runner;

            Execution(Instant scheduledAt) {
                future = new FutureTask<>(() -> {
                    if (!state.compareAndSet(0, 1)) {
                        return null;
                    }
                    runner = Thread.currentThread();
                    enterExecution();
                    try {
                        executeOccurrence(scheduledAt);
                    } finally {
                        exitExecution();
                        runner = null;
                        releaseStartedReservation(state);
                    }
                    return null;
                }) {
                    @Override
                    protected void done() {
                        synchronized (lock) {
                            executions.remove(Execution.this);
                        }
                        if (isCancelled()) {
                            releaseUnstartedReservation(state);
                        }
                    }
                };
            }

            void cancel(boolean mayInterruptIfRunning, @Nullable Thread excludedRunningThread) {
                if (cancelBeforeStart()) {
                    return;
                }
                Thread currentRunner = runner;
                if (mayInterruptIfRunning
                        && (excludedRunningThread == null
                        || currentRunner == null
                        || currentRunner.threadId() != excludedRunningThread.threadId())) {
                    future.cancel(true);
                }
            }

            private boolean cancelBeforeStart() {
                if (!state.compareAndSet(0, 2)) {
                    return false;
                }
                try {
                    future.cancel(false);
                } finally {
                    // 上面的状态转换会阻止 callable 启动；done() 看到状态 2 后不会再次释放预留计数。
                    releaseReservationAccounting();
                }
                return true;
            }
        }

        private void emitTerminal(
                TaskEventStatus status,
                Instant scheduledAt,
                @Nullable Throwable failure,
                @Nullable String reason) {
            emit(new TaskEvent(
                    id, status, scheduledAt, null, clock.instant(), failure, reason));
        }

        private void requireActive() {
            if (cancelled || closed.get()) {
                throw new IllegalStateException("Scheduled task is no longer active: " + id);
            }
        }
    }

    /**
     * 构建实例级调度器状态的构建器。
     */
    public static final class Builder {

        private Clock clock = Clock.systemUTC();
        private @Nullable ExecutorService executor;
        private int maxConcurrentExecutions = 256;
        private int maxPendingExecutions = 1_024;
        private Duration shutdownTimeout = Duration.ofSeconds(30);
        private TaskEventListener listener = ignored -> {
        };
        private String threadNamePrefix = "lava-schedule";

        private Builder() {
        }

        /**
         * 设置计算下一次触发时间所用的时钟。
         *
         * @param clock 调度时钟
         * @return 当前构建器
         */
        public Builder clock(Clock clock) {
            this.clock = ValidationUtils.requireNonNull(clock, "clock must not be null");
            return this;
        }

        /**
         * 提供借入的任务执行器，调度器绝不会关闭它。
         *
         * @param executor 用于运行任务的执行器
         * @return 当前构建器
         */
        public Builder executor(ExecutorService executor) {
            this.executor = ValidationUtils.requireNonNull(executor, "executor must not be null");
            return this;
        }

        /**
         * 配置调度器拥有的有界虚拟线程执行器。
         *
         * @param maxConcurrentExecutions 允许同时执行的最大任务数
         * @param maxPendingExecutions    等待执行的最大任务数
         * @return 当前构建器
         */
        public Builder executionBounds(int maxConcurrentExecutions, int maxPendingExecutions) {
            requirePositive(maxConcurrentExecutions, "maxConcurrentExecutions");
            requirePositive(maxPendingExecutions, "maxPendingExecutions");
            this.maxConcurrentExecutions = maxConcurrentExecutions;
            this.maxPendingExecutions = maxPendingExecutions;
            return this;
        }

        /**
         * 设置关闭时等待活动任务结束的默认时间。
         *
         * @param shutdownTimeout 非负的等待时间
         * @return 当前构建器
         */
        public Builder shutdownTimeout(Duration shutdownTimeout) {
            requireNonNegative(shutdownTimeout, "shutdownTimeout");
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        /**
         * 设置接收任务终态事件的监听器。
         *
         * @param listener 任务事件监听器
         * @return 当前构建器
         */
        public Builder listener(TaskEventListener listener) {
            this.listener = ValidationUtils.requireNonNull(listener, "listener must not be null");
            return this;
        }

        /**
         * 设置调度器自有协调线程的名称前缀。
         *
         * @param threadNamePrefix 非空白的线程名称前缀
         * @return 当前构建器
         */
        public Builder threadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = requireNotBlank(threadNamePrefix, "threadNamePrefix");
            return this;
        }

        /**
         * 创建新的调度器。
         *
         * @return 新的调度器
         */
        public LavaScheduler build() {
            return new LavaScheduler(this);
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        ValidationUtils.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static String requireNotBlank(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank");
    }
}
