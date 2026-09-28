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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.*;
import java.util.Date;
import java.util.TimeZone;

/**
 * 不可变的进程内调度。Quartz 仅用于计算 Cron 触发时刻；不会向 Quartz 传递用户对象，
 * 也不会创建 Quartz 调度器。
 */
public final class Trigger {

    @FunctionalInterface
    private interface NextFireTime {
        @Nullable Instant after(Instant afterExclusive);
    }

    @FunctionalInterface
    private interface FirstFireTime {
        @Nullable Instant from(Instant now);
    }

    private final FirstFireTime firstFireTime;
    private final NextFireTime nextFireTime;
    /**
     * 固定延迟；非 null 时下一次触发取决于执行完成时刻，由调度器在完成后计算，{@link #nextFireTime} 恒为 null。
     */
    private final @Nullable Duration fixedDelay;
    private final MisfirePolicy misfirePolicy;

    private Trigger(FirstFireTime firstFireTime, NextFireTime nextFireTime) {
        this(firstFireTime, nextFireTime, null, MisfirePolicy.FIRE_ONCE_NOW);
    }

    private Trigger(
            FirstFireTime firstFireTime,
            NextFireTime nextFireTime,
            @Nullable Duration fixedDelay,
            MisfirePolicy misfirePolicy) {
        this.firstFireTime = firstFireTime;
        this.nextFireTime = nextFireTime;
        this.fixedDelay = fixedDelay;
        this.misfirePolicy = misfirePolicy;
    }

    /**
     * 创建在指定时刻触发一次的触发器。
     *
     * @param instant 绝对触发时刻
     * @return 一次性触发器
     */
    public static Trigger at(Instant instant) {
        Instant fireTime = ValidationUtils.requireNonNull(instant, "instant must not be null");
        return new Trigger(
                ignored -> fireTime,
                afterExclusive -> afterExclusive.isBefore(fireTime) ? fireTime : null);
    }

    /**
     * 创建在指定延迟后触发一次的触发器。
     *
     * @param delay 相对于调度器时钟的延迟；允许为零
     * @return 一次性触发器
     */
    public static Trigger after(Duration delay) {
        requirePositiveOrZero(delay, "delay");
        return new Trigger(now -> safePlus(now, delay), ignored -> null);
    }

    /**
     * 创建固定频率触发器；首次执行发生在一个间隔之后。
     *
     * @param interval 两次计划执行之间的间隔
     * @return 固定频率触发器
     */
    public static Trigger fixedRate(Duration interval) {
        return fixedRate(interval, interval);
    }

    /**
     * 创建具有相对初始延迟的固定频率触发器。
     *
     * @param initialDelay 首次执行相对调度时钟的延迟
     * @param interval     两次计划执行之间的间隔
     * @return 固定频率触发器
     */
    public static Trigger fixedRate(Duration initialDelay, Duration interval) {
        requirePositiveOrZero(initialDelay, "initialDelay");
        requirePositive(interval, "interval");
        return new Trigger(
                now -> safePlus(now, initialDelay),
                previous -> safePlus(previous, interval));
    }

    /**
     * 创建锚定于绝对首次执行时刻的固定频率触发器。
     *
     * @param firstExecution 首次执行的绝对时刻
     * @param interval       两次计划执行之间的间隔
     * @return 固定频率触发器
     */
    public static Trigger fixedRate(Instant firstExecution, Duration interval) {
        Instant first = ValidationUtils.requireNonNull(firstExecution, "firstExecution must not be null");
        requirePositive(interval, "interval");
        return new Trigger(
                ignored -> first,
                previous -> safePlus(previous, interval));
    }

    /**
     * 创建固定延迟触发器；首次执行发生在一个延迟之后，此后每次执行结束再等待一个延迟。
     *
     * @param delay 上一次执行结束到下一次执行开始的间隔
     * @return 固定延迟触发器
     */
    public static Trigger fixedDelay(Duration delay) {
        return fixedDelay(delay, delay);
    }

    /**
     * 创建具有相对初始延迟的固定延迟触发器。
     *
     * <p>下一次执行从上一次执行结束（含被跳过或被执行器拒绝）时开始计时，因此由触发器驱动的执行彼此不重叠；
     * 只有 {@link ScheduledTask#triggerNow()} 可能在 {@link ConcurrencyPolicy#PARALLEL} 下与其并行，
     * 且不会改变后续节奏。执行进行中时 {@link ScheduledTask#nextExecution()} 为 null。
     *
     * @param initialDelay 首次执行相对调度时钟的延迟；允许为零
     * @param delay        上一次执行结束到下一次执行开始的间隔
     * @return 固定延迟触发器
     */
    public static Trigger fixedDelay(Duration initialDelay, Duration delay) {
        requirePositiveOrZero(initialDelay, "initialDelay");
        requirePositive(delay, "delay");
        return new Trigger(
                now -> safePlus(now, initialDelay),
                ignored -> null,
                delay,
                MisfirePolicy.FIRE_ONCE_NOW);
    }

    /**
     * 创建 UTC Cron 触发器。
     *
     * @param expression Quartz Cron 表达式
     * @return Cron 触发器
     */
    public static Trigger cron(String expression) {
        return cron(expression, ZoneOffset.UTC);
    }

    /**
     * 在显式时区中创建 Cron 触发器，并立即校验表达式。
     *
     * @param expression Quartz Cron 表达式
     * @param zoneId     计算本地日期和时间时使用的时区
     * @return Cron 触发器
     * @throws IllegalArgumentException 表达式无效或为空白
     */
    public static Trigger cron(String expression, ZoneId zoneId) {
        String cron = requireNotBlank(expression, "expression");
        ZoneId zone = ValidationUtils.requireNonNull(zoneId, "zoneId must not be null");
        // 在构造时解析一次，确保错误表达式在调用处失败。
        newCronExpression(cron, zone);
        NextFireTime calculator = afterExclusive -> {
            CronExpression parsed = newCronExpression(cron, zone);
            Date next = parsed.getNextValidTimeAfter(Date.from(afterExclusive));
            return next == null ? null : next.toInstant();
        };
        return new Trigger(calculator::after, calculator);
    }

    /**
     * 返回使用指定错过触发策略的新触发器，当前触发器不变。
     *
     * @param misfirePolicy 计划时刻被错过时的处理方式
     * @return 新的触发器
     */
    public Trigger withMisfirePolicy(MisfirePolicy misfirePolicy) {
        ValidationUtils.requireNonNull(misfirePolicy, "misfirePolicy must not be null");
        return new Trigger(firstFireTime, nextFireTime, fixedDelay, misfirePolicy);
    }

    MisfirePolicy misfirePolicy() {
        return misfirePolicy;
    }

    boolean isFixedDelay() {
        return fixedDelay != null;
    }

    /**
     * 固定延迟触发器在一次执行结束后的下一次触发时刻。
     */
    Instant nextFixedDelayFireTime(Instant completedAt) {
        return safePlus(completedAt, ValidationUtils.requireNonNull(fixedDelay, "not a fixed-delay trigger"));
    }

    @Nullable Instant firstFireTime(Instant now) {
        return firstFireTime.from(now);
    }

    @Nullable Instant nextFireTime(Instant afterExclusive) {
        return nextFireTime.after(afterExclusive);
    }

    /**
     * 返回严格晚于指定时刻的下一次执行；不存在时返回空。
     *
     * <p>固定延迟触发器的下一次执行取决于执行完成时刻，无法仅凭时刻推算，始终返回 null。
     *
     * @param afterExclusive 查询起点，不包含该时刻
     * @return 下一次执行时刻；不存在时为 {@code null}
     */
    public @Nullable Instant nextExecutionAfter(Instant afterExclusive) {
        return nextFireTime(ValidationUtils.requireNonNull(afterExclusive, "afterExclusive must not be null"));
    }

    private static CronExpression newCronExpression(String expression, ZoneId zoneId) {
        try {
            CronExpression parsed = new CronExpression(expression);
            parsed.setTimeZone(TimeZone.getTimeZone(zoneId));
            return parsed;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid Cron expression", e);
        }
    }

    private static Instant safePlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (DateTimeException | ArithmeticException e) {
            throw new ScheduleException("Trigger time is outside the supported Instant range", e);
        }
    }

    private static void requirePositive(Duration duration, String name) {
        requirePositiveOrZero(duration, name);
        if (duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositiveOrZero(Duration duration, String name) {
        ValidationUtils.requireNonNull(duration, name + " must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static String requireNotBlank(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank");
    }
}
