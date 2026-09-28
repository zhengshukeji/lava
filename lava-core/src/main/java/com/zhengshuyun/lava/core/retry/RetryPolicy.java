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
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 不可变且线程安全的重试策略，同时负责按策略执行操作。
 *
 * <pre>{@code
 * RetryPolicy<String> policy = RetryPolicy.<String>builder()
 *         .maxAttempts(3)
 *         .fixedDelay(Duration.ofMillis(200))
 *         .retryOnException(IOException.class)
 *         .build();
 *
 * String response = policy.call(() -> fetchRemoteData());
 * }</pre>
 *
 * <p>重试耗尽后原样抛出最后一次异常；{@link InterruptedException} 绝不重试，并先恢复线程中断标记。
 *
 * @param <T> 操作结果类型
 */
public final class RetryPolicy<T> {

    /**
     * 总尝试次数，包含首次执行。
     */
    private final int maxAttempts;

    /**
     * 每次可重试尝试结束后计算等待时长的策略。
     */
    private final RetryDelayStrategy delayStrategy;

    /**
     * 根据抛出的异常决定是否继续重试的条件。
     */
    private final Predicate<? super Exception> exceptionCondition;

    /**
     * 根据正常返回的结果决定是否继续重试的条件。
     */
    private final Predicate<? super T> resultCondition;

    /**
     * 每次尝试结束后接收观测结果的监听器；监听器抛出的异常会直接中止重试。
     */
    private final Consumer<? super RetryAttempt<T>> listener;

    private RetryPolicy(Builder<T> builder) {
        this.maxAttempts = builder.maxAttempts;
        this.delayStrategy = builder.delayStrategy;
        this.exceptionCondition = builder.exceptionCondition;
        this.resultCondition = builder.resultCondition;
        this.listener = builder.listener;
    }

    /**
     * 创建构建器：默认最多尝试 3 次、不等待、重试所有 {@link Exception}、不按成功结果重试。
     *
     * @param <T> 被执行操作的结果类型
     * @return 新的策略构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 按策略执行有返回值的操作。
     *
     * @param action 待执行的操作
     * @return 最后一次不再重试的成功结果
     * @throws Exception 操作最终失败，或线程被中断
     */
    public T call(Callable<? extends T> action) throws Exception {
        ValidationUtils.requireNonNull(action, "action");
        return execute(action, true);
    }

    /**
     * 按策略执行无返回值的操作；只按异常决定是否重试，结果条件不参与判断。
     *
     * @param action 待执行的操作
     * @throws Exception 操作最终失败，或线程被中断
     */
    public void run(CheckedRunnable action) throws Exception {
        ValidationUtils.requireNonNull(action, "action");
        execute(() -> {
            action.run();
            return null;
        }, false);
    }

    private T execute(Callable<? extends T> action, boolean checkResult) throws Exception {
        for (int attempt = 1; ; attempt++) {
            boolean lastAttempt = attempt >= maxAttempts;
            T result;
            try {
                result = action.call();
            } catch (InterruptedException interrupted) {
                // 中断代表调用方要求停止，恢复标记后立即终止，不再进入下一轮
                Thread.currentThread().interrupt();
                listener.accept(new RetryAttempt<>(
                        attempt, maxAttempts, null, interrupted, false, Duration.ZERO));
                throw interrupted;
            } catch (Exception exception) {
                // 最后一次尝试不再评估条件，避免条件本身的副作用或异常干扰最终结果
                boolean willRetry = !lastAttempt && exceptionCondition.test(exception);
                Duration delay = nextDelay(attempt, willRetry);
                listener.accept(new RetryAttempt<>(
                        attempt, maxAttempts, null, exception, willRetry, delay));
                if (!willRetry) {
                    throw exception;
                }
                sleep(delay);
                continue;
            }

            boolean willRetry = checkResult && !lastAttempt && resultCondition.test(result);
            Duration delay = nextDelay(attempt, willRetry);
            listener.accept(new RetryAttempt<>(attempt, maxAttempts, result, null, willRetry, delay));
            if (!willRetry) {
                return result;
            }
            sleep(delay);
        }
    }

    private Duration nextDelay(int attempt, boolean willRetry) {
        if (!willRetry) {
            return Duration.ZERO;
        }
        Duration delay = ValidationUtils.requireNonNull(
                delayStrategy.delayAfter(attempt), "retry delay must not be null");
        ValidationUtils.requireTrue(!delay.isNegative(), "retry delay must not be negative");
        return delay;
    }

    private static void sleep(Duration delay) throws InterruptedException {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    /**
     * {@link RetryPolicy} 的构建器。
     *
     * @param <T> 被执行操作的结果类型
     */
    public static final class Builder<T> {

        private int maxAttempts = 3;
        private RetryDelayStrategy delayStrategy = RetryDelayStrategy.none();
        private Predicate<? super Exception> exceptionCondition = failure -> true;
        private Predicate<? super T> resultCondition = result -> false;
        private Consumer<? super RetryAttempt<T>> listener = attempt -> {
        };

        private Builder() {
        }

        /**
         * 设置总尝试次数，包含首次调用。
         *
         * @param maxAttempts 总尝试次数，至少为 1
         * @return 当前构建器
         */
        public Builder<T> maxAttempts(int maxAttempts) {
            ValidationUtils.requireTrue(maxAttempts >= 1, "maxAttempts must be >= 1");
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 设置每次重试前的延迟策略，常用策略见 {@link RetryDelayStrategy} 的静态工厂。
         *
         * @param delayStrategy 延迟计算策略
         * @return 当前构建器
         */
        public Builder<T> delay(RetryDelayStrategy delayStrategy) {
            this.delayStrategy = ValidationUtils.requireNonNull(delayStrategy, "delayStrategy");
            return this;
        }

        /**
         * 设置固定重试延迟。
         *
         * @param delay 每次重试前的非负延迟
         * @return 当前构建器
         */
        public Builder<T> fixedDelay(Duration delay) {
            return delay(RetryDelayStrategy.fixed(delay));
        }

        /**
         * 设置不带随机抖动的指数退避延迟策略。
         *
         * @param initialDelay 首次重试前的非负延迟
         * @param multiplier   每次重试的延迟倍率，至少为 1
         * @param maxDelay     延迟上限，不能小于初始延迟
         * @return 当前构建器
         */
        public Builder<T> exponentialBackoff(
                Duration initialDelay, double multiplier, Duration maxDelay) {
            return delay(RetryDelayStrategy.exponential(initialDelay, multiplier, maxDelay));
        }

        /**
         * 设置带完全抖动的指数退避延迟策略，适合打散大量并发调用方的重试时机。
         *
         * @param initialDelay 首次重试前的非负延迟
         * @param multiplier   每次重试的延迟倍率，至少为 1
         * @param maxDelay     延迟上限，不能小于初始延迟
         * @return 当前构建器
         */
        public Builder<T> exponentialBackoffWithFullJitter(
                Duration initialDelay, double multiplier, Duration maxDelay) {
            return delay(RetryDelayStrategy.fullJitter(initialDelay, multiplier, maxDelay));
        }

        /**
         * 设置决定异常是否可重试的条件。
         *
         * @param condition 返回 true 时重试该异常
         * @return 当前构建器
         */
        public Builder<T> retryOnException(Predicate<? super Exception> condition) {
            this.exceptionCondition = ValidationUtils.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * 仅重试指定异常类型及其子类型。
         *
         * @param type 可重试的异常类型
         * @return 当前构建器
         */
        public Builder<T> retryOnException(Class<? extends Exception> type) {
            ValidationUtils.requireNonNull(type, "type");
            return retryOnException(type::isInstance);
        }

        /**
         * 设置决定成功结果是否仍需重试的条件，例如轮询到 "pending" 时继续。
         *
         * @param condition 返回 true 时继续重试
         * @return 当前构建器
         */
        public Builder<T> retryOnResult(Predicate<? super T> condition) {
            this.resultCondition = ValidationUtils.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * 设置每次尝试完成后接收观测结果的监听器，常用于记录日志或指标。
         *
         * @param listener 尝试结果监听器
         * @return 当前构建器
         */
        public Builder<T> listener(Consumer<? super RetryAttempt<T>> listener) {
            this.listener = ValidationUtils.requireNonNull(listener, "listener");
            return this;
        }

        /**
         * 构建不可变的重试策略。
         *
         * @return 重试策略
         */
        public RetryPolicy<T> build() {
            return new RetryPolicy<>(this);
        }
    }
}
