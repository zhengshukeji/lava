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

package com.zhengshuyun.lava.core.retry;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 计算一次可重试尝试结束后的等待时长，实现必须返回非负 {@link Duration}。
 */
@FunctionalInterface
public interface RetryDelayStrategy {

    /**
     * 返回一次可重试尝试结束后的等待时长。
     *
     * @param attempt 已完成的尝试序号，从 1 开始
     * @return 下一次重试前的非负延迟
     */
    Duration delayAfter(int attempt);

    /**
     * 创建不等待的延迟策略。
     *
     * @return 始终返回零延迟的策略
     */
    static RetryDelayStrategy none() {
        return fixed(Duration.ZERO);
    }

    /**
     * 创建固定延迟策略。
     *
     * @param delay 每次重试前的非负延迟
     * @return 固定延迟策略
     */
    static RetryDelayStrategy fixed(Duration delay) {
        requireNonNegative(delay, "delay");
        return attempt -> delay;
    }

    /**
     * 创建不带抖动、设有上限的指数退避策略。
     *
     * @param initialDelay 首次重试前的非负延迟
     * @param multiplier   每次重试的延迟倍率，至少为 1
     * @param maxDelay     延迟上限，不能小于初始延迟
     * @return 指数退避策略
     */
    static RetryDelayStrategy exponential(Duration initialDelay, double multiplier, Duration maxDelay) {
        return exponentialInternal(initialDelay, multiplier, maxDelay, false);
    }

    /**
     * 创建带完全抖动、设有上限的指数退避策略：每次延迟在
     * {@code [0, min(maxDelay, initialDelay * multiplier^(attempt-1)))} 区间内均匀随机取值。
     *
     * @param initialDelay 首次重试前的非负延迟
     * @param multiplier   每次重试的延迟倍率，至少为 1
     * @param maxDelay     延迟上限，不能小于初始延迟
     * @return 带完全抖动的指数退避策略
     */
    static RetryDelayStrategy fullJitter(Duration initialDelay, double multiplier, Duration maxDelay) {
        return exponentialInternal(initialDelay, multiplier, maxDelay, true);
    }

    private static RetryDelayStrategy exponentialInternal(
            Duration initialDelay, double multiplier, Duration maxDelay, boolean jitter) {
        requireNonNegative(initialDelay, "initialDelay");
        requireNonNegative(maxDelay, "maxDelay");
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be finite and >= 1.0");
        }
        if (initialDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("initialDelay must be <= maxDelay");
        }

        return attempt -> {
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt must be >= 1");
            }
            Duration cap = exponentialCap(initialDelay, multiplier, maxDelay, attempt);
            return jitter ? multiply(cap, ThreadLocalRandom.current().nextDouble()) : cap;
        };
    }

    private static Duration exponentialCap(
            Duration initialDelay, double multiplier, Duration maxDelay, int attempt) {
        if (initialDelay.isZero() || initialDelay.equals(maxDelay) || attempt == 1) {
            return initialDelay;
        }

        double initialSeconds = initialDelay.getSeconds() + initialDelay.getNano() / 1_000_000_000.0;
        double maxSeconds = maxDelay.getSeconds() + maxDelay.getNano() / 1_000_000_000.0;
        double candidate = initialSeconds * Math.pow(multiplier, attempt - 1.0);
        if (!Double.isFinite(candidate) || candidate >= maxSeconds) {
            return maxDelay;
        }
        return fromSeconds(candidate);
    }

    private static Duration multiply(Duration duration, double factor) {
        if (duration.isZero() || factor == 0.0) {
            return Duration.ZERO;
        }
        double seconds = (duration.getSeconds() + duration.getNano() / 1_000_000_000.0) * factor;
        return fromSeconds(seconds);
    }

    private static Duration fromSeconds(double seconds) {
        long wholeSeconds = (long) seconds;
        int nanos = (int) ((seconds - wholeSeconds) * 1_000_000_000.0);
        return Duration.ofSeconds(wholeSeconds, Math.max(0, nanos));
    }

    private static void requireNonNegative(Duration duration, String name) {
        ValidationUtils.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
