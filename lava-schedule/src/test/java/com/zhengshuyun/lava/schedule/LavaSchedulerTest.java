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

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LavaSchedulerTest {

    @Test
    void executesOneShotAndEmitsStructuredSuccessEvent() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(event -> {
            events.add(event);
            completed.countDown();
        }).build()) {
            ScheduledTask task = scheduler.schedule(
                    "once", () -> {
                    }, Trigger.after(Duration.ofMillis(20)));

            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertEquals("once", task.id());
            assertEquals(TaskEventStatus.SUCCESS, events.getFirst().status());
            assertNotNull(events.getFirst().startedAt());
            assertFalse(events.getFirst().completedAt().isBefore(events.getFirst().startedAt()));
            assertNull(events.getFirst().failure());
            assertNull(task.nextExecution());
        }
    }

    @Test
    void zeroDelayIsDueNowRatherThanAClockReadMisfire() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        try (LavaScheduler scheduler = LavaScheduler.create()) {
            scheduler.schedule("now", completed::countDown, Trigger.after(Duration.ZERO));

            assertTrue(completed.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void generatedTaskIdsAreUUIDv7() {
        try (LavaScheduler scheduler = LavaScheduler.create()) {
            ScheduledTask task = scheduler.schedule(() -> {
            }, Trigger.after(Duration.ofHours(1)));
            java.util.UUID id = java.util.UUID.fromString(task.id());

            assertEquals(7, id.version());
        }
    }

    @Test
    void initialMisfireListenerCannotBlockScheduleWhileClosingTheScheduler() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        CountDownLatch listenerStarted = new CountDownLatch(1);
        CountDownLatch allowClose = new CountDownLatch(1);
        AtomicReference<LavaScheduler> schedulerReference = new AtomicReference<>();
        LavaScheduler scheduler = LavaScheduler.builder()
                .clock(Clock.fixed(now, ZoneOffset.UTC))
                .listener(event -> {
                    if (event.taskId().equals("initial-misfire")) {
                        listenerStarted.countDown();
                        awaitUnchecked(allowClose);
                        schedulerReference.get().close();
                    }
                })
                .build();
        schedulerReference.set(scheduler);

        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            var scheduled = caller.submit(() -> scheduler.schedule(
                    "initial-misfire",
                    () -> {
                    },
                    Trigger.at(now.minusSeconds(1)),
                    ConcurrencyPolicy.SKIP_IF_RUNNING));

            assertNotNull(scheduled.get(2, TimeUnit.SECONDS));
            assertTrue(listenerStarted.await(2, TimeUnit.SECONDS));
            allowClose.countDown();
            assertTrue(awaitClosed(scheduler));
        } finally {
            allowClose.countDown();
            scheduler.close(Duration.ZERO);
        }
    }

    @Test
    void defaultSerialSkipNeverOverlaps() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(events::add).build()) {
            ScheduledTask task = scheduler.schedule("serial", () -> {
                started.countDown();
                awaitUnchecked(release);
            }, Trigger.after(Duration.ofHours(1)));

            task.triggerNow();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            task.triggerNow();

            assertTrue(awaitStatus(events, TaskEventStatus.SKIPPED));
            release.countDown();
            assertTrue(awaitStatus(events, TaskEventStatus.SUCCESS));
        }
    }

    @Test
    void parallelPolicyOverlapsWithinExecutorBounds() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder()
                .executionBounds(2, 1)
                .listener(events::add)
                .build()) {
            ScheduledTask task = scheduler.schedule("parallel", () -> {
                executions.incrementAndGet();
                started.countDown();
                awaitUnchecked(release);
            }, Trigger.after(Duration.ofHours(1)), ConcurrencyPolicy.PARALLEL);

            task.triggerNow();
            task.triggerNow();
            assertTrue(started.await(2, TimeUnit.SECONDS), "PARALLEL 允许同一任务重叠执行");
            // 两个执行占满并发、一个进入执行器队列，第四个被执行器拒绝
            task.triggerNow();
            task.triggerNow();
            assertTrue(awaitStatus(events, TaskEventStatus.REJECTED));
            release.countDown();
            assertTrue(awaitCount(executions, 3));
        }
    }

    @Test
    void cancelPreventsSubmittedButUnstartedWorkOnBorrowedExecutor() throws Exception {
        ExecutorService borrowed = Executors.newSingleThreadExecutor();
        CountDownLatch executorOccupied = new CountDownLatch(1);
        CountDownLatch releaseExecutor = new CountDownLatch(1);
        borrowed.execute(() -> {
            executorOccupied.countDown();
            awaitUnchecked(releaseExecutor);
        });
        assertTrue(executorOccupied.await(2, TimeUnit.SECONDS));

        AtomicInteger executions = new AtomicInteger();
        LavaScheduler scheduler = LavaScheduler.builder().executor(borrowed).build();
        try {
            ScheduledTask task = scheduler.schedule(
                    "cancel-queued", executions::incrementAndGet, Trigger.after(Duration.ofHours(1)));
            task.triggerNow();

            assertTrue(task.cancel());
            releaseExecutor.countDown();
            borrowed.submit(() -> {
            }).get(2, TimeUnit.SECONDS);

            assertEquals(0, executions.get());
            assertFalse(borrowed.isShutdown());
        } finally {
            releaseExecutor.countDown();
            scheduler.close(Duration.ZERO);
            borrowed.shutdownNow();
        }
    }

    @Test
    void coordinatorDelayStillRunsTheLateOccurrenceOnce() throws Exception {
        CountDownLatch runningStarted = new CountDownLatch(1);
        CountDownLatch releaseRunning = new CountDownLatch(1);
        CountDownLatch coordinatorBlocked = new CountDownLatch(1);
        CountDownLatch releaseCoordinator = new CountDownLatch(1);
        AtomicInteger lateExecutions = new AtomicInteger();
        CountDownLatch lateRan = new CountDownLatch(1);

        // 监听器在协调线程上阻塞，使后注册任务的计划时刻被错过
        LavaScheduler scheduler = LavaScheduler.builder().listener(event -> {
            if (event.taskId().equals("coordinator-blocker")
                    && event.status() == TaskEventStatus.SKIPPED) {
                coordinatorBlocked.countDown();
                awaitUnchecked(releaseCoordinator);
            }
        }).build();
        try {
            ScheduledTask blocker = scheduler.schedule("coordinator-blocker", () -> {
                runningStarted.countDown();
                awaitUnchecked(releaseRunning);
            }, Trigger.after(Duration.ofMillis(30)));
            blocker.triggerNow();
            assertTrue(runningStarted.await(2, TimeUnit.SECONDS));

            scheduler.schedule("late", () -> {
                lateExecutions.incrementAndGet();
                lateRan.countDown();
            }, Trigger.after(Duration.ofMillis(100)));

            assertTrue(coordinatorBlocked.await(2, TimeUnit.SECONDS));
            Thread.sleep(150);
            releaseCoordinator.countDown();

            assertTrue(lateRan.await(2, TimeUnit.SECONDS), "错过的触发仍应执行一次");
            assertEquals(1, lateExecutions.get());
        } finally {
            releaseCoordinator.countDown();
            releaseRunning.countDown();
            scheduler.close();
        }
    }

    @Test
    void failuresAndListenerFailuresDoNotStopLaterExecutions() throws Exception {
        CountDownLatch ranTwice = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();
        List<TaskEvent> captured = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(event -> {
            captured.add(event);
            throw new IllegalStateException("listener failure");
        }).build()) {
            ScheduledTask task = scheduler.schedule("fail", () -> {
                ranTwice.countDown();
                if (attempts.getAndIncrement() == 0) {
                    throw new IllegalArgumentException("task failure");
                }
            }, Trigger.after(Duration.ofHours(1)));

            task.triggerNow();
            assertTrue(awaitStatus(captured, TaskEventStatus.FAILURE));
            task.triggerNow();
            assertTrue(ranTwice.await(2, TimeUnit.SECONDS));
            assertTrue(awaitStatus(captured, TaskEventStatus.SUCCESS));
            assertEquals(IllegalArgumentException.class,
                    captured.stream()
                            .filter(event -> event.status() == TaskEventStatus.FAILURE)
                            .findFirst()
                            .orElseThrow()
                            .failure()
                            .getClass());
        }
    }

    @Test
    void missedOccurrencesCoalesceIntoOneExecution() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AtomicInteger executions = new AtomicInteger();
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder()
                .clock(clock)
                .listener(events::add)
                .build()) {
            // 积压一万多次触发也只合并执行一次，并直接推进到当前时刻之后
            ScheduledTask task = scheduler.schedule("backlog", executions::incrementAndGet,
                    Trigger.fixedRate(now.minusSeconds(10_001), Duration.ofSeconds(1)));

            assertTrue(awaitStatus(events, TaskEventStatus.SUCCESS));
            Thread.sleep(50);
            assertEquals(1, executions.get());
            assertTrue(events.stream().noneMatch(event -> event.status() == TaskEventStatus.SKIPPED));
            assertEquals(now.plusSeconds(1), task.nextExecution());
        }
    }

    @Test
    void smallMisfireKeepsFixedRatePhase() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder()
                .clock(Clock.fixed(now, ZoneOffset.UTC))
                .listener(events::add)
                .build()) {
            // 错过 3 次触发：合并执行一次后，下一次仍落在原相位 now+0.5s，而不是从当前时刻重算的 now+1s
            Instant first = now.minusMillis(2_500);
            ScheduledTask task = scheduler.schedule("phase", () -> {
            }, Trigger.fixedRate(first, Duration.ofSeconds(1)));

            assertTrue(awaitStatus(events, TaskEventStatus.SUCCESS));
            assertEquals(now.plusMillis(500), task.nextExecution());
            assertEquals(first, task.previousExecution());
        }
    }

    @Test
    void fixedDelayWaitsForCompletionBeforeNextRun() throws Exception {
        Duration delay = Duration.ofMillis(50);
        List<TaskEvent> successes = new CopyOnWriteArrayList<>();
        CountDownLatch ranThreeTimes = new CountDownLatch(3);
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(event -> {
            if (event.status() == TaskEventStatus.SUCCESS) {
                successes.add(event);
                ranThreeTimes.countDown();
            }
        }).build()) {
            // 任务耗时大于延迟：固定频率会紧接着触发，固定延迟必须在完成后再等满一个延迟
            scheduler.schedule("fixed-delay", () -> sleepUnchecked(Duration.ofMillis(80)),
                    Trigger.fixedDelay(Duration.ZERO, delay));

            assertTrue(ranThreeTimes.await(2, TimeUnit.SECONDS));
            for (int i = 1; i < 3; i++) {
                Duration gap = Duration.between(successes.get(i - 1).completedAt(), successes.get(i).startedAt());
                assertTrue(gap.compareTo(delay) >= 0, "两次执行之间的间隔不足一个延迟: " + gap);
            }
        }
    }

    @Test
    void skipMisfirePolicyDropsMissedOccurrences() throws Exception {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        AtomicInteger executions = new AtomicInteger();
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder()
                .clock(Clock.fixed(now, ZoneOffset.UTC))
                .listener(events::add)
                .build()) {
            Instant first = now.minusMillis(2_500);
            ScheduledTask rate = scheduler.schedule("skip-rate", executions::incrementAndGet,
                    Trigger.fixedRate(first, Duration.ofSeconds(1)).withMisfirePolicy(MisfirePolicy.SKIP));
            ScheduledTask once = scheduler.schedule("skip-once", executions::incrementAndGet,
                    Trigger.at(now.minusSeconds(1)).withMisfirePolicy(MisfirePolicy.SKIP));

            assertTrue(awaitCount(events, TaskEventStatus.SKIPPED, 2));
            Thread.sleep(50);
            assertEquals(0, executions.get());
            // 跳过后仍保持固定频率相位；一次性触发器被跳过后不再执行
            assertEquals(now.plusMillis(500), rate.nextExecution());
            assertNull(rate.previousExecution());
            assertNull(once.nextExecution());
        }
    }

    @Test
    void rescheduleKeepsRunningExecutionInConcurrencyCheck() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(events::add).build()) {
            ScheduledTask task = scheduler.schedule("reschedule", () -> {
                executions.incrementAndGet();
                started.countDown();
                awaitUnchecked(release);
            }, Trigger.after(Duration.ofHours(1)));
            task.triggerNow();
            assertTrue(started.await(2, TimeUnit.SECONDS));

            // 旧执行仍在运行：新触发器的立即触发必须被跳过，而不是与其重叠
            task.reschedule(Trigger.after(Duration.ZERO));
            assertTrue(awaitStatus(events, TaskEventStatus.SKIPPED));
            assertEquals(1, executions.get());

            Instant future = Instant.now().plus(Duration.ofHours(2));
            task.reschedule(Trigger.at(future));
            assertEquals(future, task.nextExecution());
            release.countDown();
            assertTrue(awaitStatus(events, TaskEventStatus.SUCCESS));
        }
    }

    @Test
    void rescheduleStopsTheOldFixedDelayChain() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        List<TaskEvent> events = new CopyOnWriteArrayList<>();
        try (LavaScheduler scheduler = LavaScheduler.builder().listener(events::add).build()) {
            ScheduledTask task = scheduler.schedule("chain", () -> {
                executions.incrementAndGet();
                started.countDown();
                awaitUnchecked(release);
            }, Trigger.fixedDelay(Duration.ZERO, Duration.ofMillis(20)));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            // 固定延迟执行进行中，下一次时刻要等完成后才知道
            assertNull(task.nextExecution());

            task.reschedule(Trigger.after(Duration.ofHours(1)));
            Instant expected = task.nextExecution();
            release.countDown();
            assertTrue(awaitStatus(events, TaskEventStatus.SUCCESS));
            Thread.sleep(100);

            assertEquals(1, executions.get());
            assertEquals(expected, task.nextExecution());
        }
    }

    @Test
    void tasksListsOnlyActiveTasks() {
        try (LavaScheduler scheduler = LavaScheduler.create()) {
            scheduler.schedule("a", () -> {
            }, Trigger.after(Duration.ofHours(1)));
            scheduler.schedule("b", () -> {
            }, Trigger.after(Duration.ofHours(1)));

            assertEquals(Set.of("a", "b"),
                    scheduler.tasks().stream().map(ScheduledTask::id).collect(Collectors.toSet()));
            scheduler.cancel("a");
            assertEquals(List.of("b"), scheduler.tasks().stream().map(ScheduledTask::id).toList());
        }
    }

    @Test
    void pauseResumeAndCancelAreInstanceScoped() {
        try (LavaScheduler first = LavaScheduler.create(); LavaScheduler second = LavaScheduler.create()) {
            ScheduledTask firstTask = first.schedule("same-id", () -> {
            }, Trigger.after(Duration.ofHours(1)));
            ScheduledTask secondTask = second.schedule("same-id", () -> {
            }, Trigger.after(Duration.ofHours(1)));

            firstTask.pause();
            assertTrue(firstTask.isPaused());
            assertFalse(secondTask.isPaused());
            firstTask.resume();
            assertFalse(firstTask.isPaused());
            assertTrue(firstTask.cancel());
            assertFalse(firstTask.exists());
            assertTrue(secondTask.exists());
            assertFalse(first.cancel("missing"));
        }
    }

    @Test
    void borrowedExecutorIsNeverClosedAndClosedSchedulerRejectsWork() {
        ExecutorService borrowed = Executors.newSingleThreadExecutor();
        LavaScheduler scheduler = LavaScheduler.builder().executor(borrowed).build();
        scheduler.schedule("borrowed", () -> {
        }, Trigger.after(Duration.ofHours(1)));

        scheduler.close();

        assertFalse(borrowed.isShutdown());
        assertThrows(IllegalStateException.class,
                () -> scheduler.schedule("late", () -> {
                }, Trigger.after(Duration.ofSeconds(1))));
        borrowed.shutdownNow();
    }

    @Test
    void closeWaitsToTimeoutThenInterruptsOnlyItsBorrowedExecutions() throws Exception {
        ExecutorService borrowed = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        LavaScheduler scheduler = LavaScheduler.builder().executor(borrowed).build();
        ScheduledTask task = scheduler.schedule("blocking", () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, Trigger.after(Duration.ofHours(1)));
        task.triggerNow();
        assertTrue(started.await(2, TimeUnit.SECONDS));

        assertFalse(scheduler.close(Duration.ZERO));
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertFalse(borrowed.isShutdown());
        borrowed.shutdownNow();
    }

    @Test
    void taskCanCloseItsSchedulerWithoutWaitingForItself() throws Exception {
        AtomicReference<LavaScheduler> schedulerReference = new AtomicReference<>();
        AtomicReference<Boolean> closeResult = new AtomicReference<>();
        CountDownLatch closeReturned = new CountDownLatch(1);
        LavaScheduler scheduler = LavaScheduler.builder()
                .shutdownTimeout(Duration.ofSeconds(5))
                .build();
        schedulerReference.set(scheduler);
        try {
            ScheduledTask task = scheduler.schedule("self-close-task", () -> {
                closeResult.set(schedulerReference.get().close(Duration.ofSeconds(5)));
                closeReturned.countDown();
            }, Trigger.after(Duration.ofHours(1)));

            task.triggerNow();

            assertTrue(closeReturned.await(2, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, closeResult.get());
            assertTrue(scheduler.isClosed());
        } finally {
            scheduler.close(Duration.ZERO);
        }
    }

    @Test
    void terminalListenerCanCloseItsSchedulerWithoutWaitingForItself() throws Exception {
        AtomicReference<LavaScheduler> schedulerReference = new AtomicReference<>();
        AtomicReference<Boolean> closeResult = new AtomicReference<>();
        CountDownLatch closeReturned = new CountDownLatch(1);
        LavaScheduler scheduler = LavaScheduler.builder()
                .shutdownTimeout(Duration.ofSeconds(5))
                .listener(event -> {
                    if (event.taskId().equals("self-close-listener")
                            && event.status() == TaskEventStatus.SUCCESS) {
                        closeResult.set(schedulerReference.get().close(Duration.ofSeconds(5)));
                        closeReturned.countDown();
                    }
                })
                .build();
        schedulerReference.set(scheduler);
        try {
            ScheduledTask task = scheduler.schedule(
                    "self-close-listener", () -> {
                    }, Trigger.after(Duration.ofHours(1)));

            task.triggerNow();

            assertTrue(closeReturned.await(2, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, closeResult.get());
            assertTrue(scheduler.isClosed());
        } finally {
            scheduler.close(Duration.ZERO);
        }
    }

    @Test
    void duplicateIdsAndInvalidOptionsAreRejected() {
        try (LavaScheduler scheduler = LavaScheduler.create()) {
            scheduler.schedule("duplicate", () -> {
            }, Trigger.after(Duration.ofHours(1)));
            assertThrows(ScheduleException.class,
                    () -> scheduler.schedule("duplicate", () -> {
                    }, Trigger.after(Duration.ofHours(1))));
        }
        assertThrows(IllegalArgumentException.class,
                () -> LavaScheduler.builder().executionBounds(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> LavaScheduler.builder().shutdownTimeout(Duration.ofSeconds(-1)));
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new TaskEvent(" ", TaskEventStatus.SUCCESS, now, now, now, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskEvent("task", TaskEventStatus.SUCCESS, now, null, now, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskEvent("task", TaskEventStatus.SKIPPED, now, now, now, null, "paused"));
    }

    @Test
    void scheduleCloseRaceNeverLeavesAnOrphanedTask() throws Exception {
        for (int iteration = 0; iteration < 100; iteration++) {
            LavaScheduler scheduler = LavaScheduler.create();
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<ScheduledTask> returned = new AtomicReference<>();
            try (var racers = Executors.newVirtualThreadPerTaskExecutor()) {
                var scheduleFuture = racers.submit(() -> {
                    awaitUnchecked(start);
                    try {
                        returned.set(scheduler.schedule(
                                "race", () -> {
                                }, Trigger.after(Duration.ofHours(1))));
                    } catch (IllegalStateException expected) {
                        // 关闭先取得生命周期锁。
                    }
                });
                var closeFuture = racers.submit(() -> {
                    awaitUnchecked(start);
                    scheduler.close();
                });
                start.countDown();
                scheduleFuture.get();
                closeFuture.get();
            }

            assertFalse(scheduler.hasTask("race"));
            ScheduledTask task = returned.get();
            if (task != null) {
                assertFalse(task.exists());
            }
        }
    }

    private static boolean awaitStatus(List<TaskEvent> events, TaskEventStatus status)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (events.stream().anyMatch(event -> event.status() == status)) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static boolean awaitCount(List<TaskEvent> events, TaskEventStatus status, long expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (events.stream().filter(event -> event.status() == status).count() == expected) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static boolean awaitCount(AtomicInteger value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (value.get() == expected) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static boolean awaitClosed(LavaScheduler scheduler) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (scheduler.isClosed()) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static void sleepUnchecked(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
