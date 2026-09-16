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
import java.util.random.RandomGenerator;

/**
 * 根据不可变的 {@link RetryPolicy} 执行可能抛出受检异常的操作。
 *
 * <pre>{@code
 * RetryPolicy<String> policy = RetryPolicy.<String>builder()
 *         .maxAttempts(3)
 *         .fixedDelay(Duration.ofMillis(200))
 *         .retryOnException(IOException.class)
 *         .build();
 *
 * String response = new RetryExecutor().execute(policy, () -> fetchRemoteData());
 * }</pre>
 */
public final class RetryExecutor {

    /**
     * 仅供带抖动的延迟策略使用的随机数源。
     */
    private final RandomGenerator random;

    /**
     * 两次尝试之间执行等待的可替换边界，供测试注入。
     */
    private final RetrySleeper sleeper;

    /**
     * 以线程本地随机数和 {@link Thread#sleep(Duration)} 创建执行器。
     *
     * <p>随机数源只会被带抖动的延迟策略用到；固定延迟的实际等待不受其影响。
     */
    public RetryExecutor() {
        this(ThreadLocalRandom.current(), Thread::sleep);
    }

    /**
     * 创建随机数与等待均可注入的执行器，便于构造确定性测试。
     *
     * @param random  用于抖动延迟的随机数源
     * @param sleeper 执行重试等待的休眠器
     */
    public RetryExecutor(RandomGenerator random, RetrySleeper sleeper) {
        this.random = ValidationUtils.requireNonNull(random, "random");
        this.sleeper = ValidationUtils.requireNonNull(sleeper, "sleeper");
    }

    /**
     * 执行可能抛受检异常的 supplier。重试耗尽后原样抛出最后一次异常。
     * 被中断的操作绝不重试，且始终先恢复线程的中断标记。
     *
     * @param policy   重试策略
     * @param supplier 待执行的操作
     * @param <T>      操作结果类型
     * @return 最后一次不再重试的成功结果
     * @throws Exception 操作最终失败，或线程被中断
     */
    public <T> T execute(RetryPolicy<T> policy, CheckedSupplier<? extends T> supplier)
            throws Exception {
        ValidationUtils.requireNonNull(policy, "policy");
        ValidationUtils.requireNonNull(supplier, "supplier");

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            T result;
            try {
                result = supplier.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                policy.notifyListener(new RetryAttempt<>(
                        attempt,
                        policy.maxAttempts(),
                        null,
                        interrupted,
                        false,
                        Duration.ZERO));
                throw interrupted;
            } catch (Exception exception) {
                boolean willRetry =
                        attempt < policy.maxAttempts() && policy.shouldRetry(exception);
                Duration nextDelay = nextDelay(policy, attempt, willRetry);
                policy.notifyListener(new RetryAttempt<>(
                        attempt, policy.maxAttempts(), null, exception, willRetry, nextDelay));
                if (!willRetry) {
                    throw exception;
                }
                sleep(nextDelay);
                continue;
            }

            boolean willRetry =
                    attempt < policy.maxAttempts() && policy.shouldRetryResult(result);
            Duration nextDelay = nextDelay(policy, attempt, willRetry);
            policy.notifyListener(new RetryAttempt<>(
                    attempt, policy.maxAttempts(), result, null, willRetry, nextDelay));
            if (!willRetry) {
                return result;
            }
            sleep(nextDelay);
        }
        throw new AssertionError("retry loop completed without a terminal outcome");
    }

    /**
     * 执行可能抛受检异常的 runnable。
     *
     * <p>成功即返回，不涉及 {@link RetryPolicy.Builder#retryOnResult}——{@code Void} 没有
     * 可判断的结果，本方法只按异常决定是否重试。
     *
     * @param policy   重试策略
     * @param runnable 待执行的操作
     * @throws Exception 操作最终失败，或线程被中断
     */
    public void run(RetryPolicy<Void> policy, CheckedRunnable runnable) throws Exception {
        ValidationUtils.requireNonNull(policy, "policy");
        ValidationUtils.requireNonNull(runnable, "runnable");
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                runnable.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                policy.notifyListener(new RetryAttempt<>(
                        attempt,
                        policy.maxAttempts(),
                        null,
                        interrupted,
                        false,
                        Duration.ZERO));
                throw interrupted;
            } catch (Exception exception) {
                boolean willRetry =
                        attempt < policy.maxAttempts() && policy.shouldRetry(exception);
                Duration nextDelay = nextDelay(policy, attempt, willRetry);
                policy.notifyListener(new RetryAttempt<>(
                        attempt, policy.maxAttempts(), null, exception, willRetry, nextDelay));
                if (!willRetry) {
                    throw exception;
                }
                sleep(nextDelay);
                continue;
            }
            policy.notifyListener(new RetryAttempt<>(
                    attempt, policy.maxAttempts(), null, null, false, Duration.ZERO));
            return;
        }
        throw new AssertionError("retry loop completed without a terminal outcome");
    }

    private <T> Duration nextDelay(RetryPolicy<T> policy, int attempt, boolean willRetry) {
        return willRetry
                ? requireValidDelay(policy.delayStrategy().delayAfter(attempt, random))
                : Duration.ZERO;
    }

    private void sleep(Duration delay) throws InterruptedException {
        if (!delay.isZero()) {
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }

    private static Duration requireValidDelay(Duration delay) {
        ValidationUtils.requireNonNull(delay, "retry delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("retry delay must not be negative");
        }
        return delay;
    }
}
