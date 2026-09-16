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
import java.util.function.Predicate;

/**
 * 不可变且线程安全的重试策略。
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
     * 每次尝试结束后接收不可变状态的监听器。
     */
    private final RetryListener<T> listener;

    private RetryPolicy(Builder<T> builder) {
        this.maxAttempts = builder.maxAttempts;
        this.delayStrategy = builder.delayStrategy;
        this.exceptionCondition = builder.exceptionCondition;
        this.resultCondition = builder.resultCondition;
        this.listener = builder.listener;
    }

    /**
     * 创建构建器：默认重试所有 {@link Exception}、不按成功结果重试。
     *
     * @param <T> 被执行操作的结果类型
     * @return 新的策略构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    int maxAttempts() {
        return maxAttempts;
    }

    RetryDelayStrategy delayStrategy() {
        return delayStrategy;
    }

    boolean shouldRetry(Exception failure) {
        return exceptionCondition.test(failure);
    }

    boolean shouldRetryResult(T result) {
        return resultCondition.test(result);
    }

    void notifyListener(RetryAttempt<T> attempt) {
        listener.onAttempt(attempt);
    }

    public static final class Builder<T> {

        /**
         * 默认执行一次初始调用和最多两次重试。
         */
        private int maxAttempts = 3;

        /**
         * 默认不在重试前等待。
         */
        private RetryDelayStrategy delayStrategy = RetryDelayStrategy.none();

        /**
         * 默认所有 {@link Exception} 均可重试。
         */
        private Predicate<? super Exception> exceptionCondition = failure -> true;

        /**
         * 默认正常返回后不再重试。
         */
        private Predicate<? super T> resultCondition = result -> false;

        /**
         * 默认不处理尝试状态。
         */
        private RetryListener<T> listener = attempt -> {
        };

        private Builder() {
        }

        /**
         * 设置总尝试次数，包含首次调用。
         *
         * @param maxAttempts 总尝试次数，至少为 1
         */
        public Builder<T> maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 设置每次重试前的延迟策略。
         *
         * @param delayStrategy 延迟计算策略
         */
        public Builder<T> delay(RetryDelayStrategy delayStrategy) {
            this.delayStrategy = ValidationUtils.requireNonNull(delayStrategy, "delayStrategy");
            return this;
        }

        /**
         * 设置固定重试延迟。
         *
         * @param delay 每次重试前的延迟
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
         */
        public Builder<T> exponentialBackoff(
                Duration initialDelay, double multiplier, Duration maxDelay) {
            return delay(RetryDelayStrategy.exponential(initialDelay, multiplier, maxDelay));
        }

        /**
         * 设置带完全抖动的指数退避延迟策略。
         *
         * <p>每次延迟在当前退避上限内随机取值，适合打散大量并发调用方的重试时机。
         *
         * @param initialDelay 首次重试前的非负延迟
         * @param multiplier   每次重试的延迟倍率，至少为 1
         * @param maxDelay     延迟上限，不能小于初始延迟
         */
        public Builder<T> exponentialBackoffWithFullJitter(
                Duration initialDelay, double multiplier, Duration maxDelay) {
            return delay(RetryDelayStrategy.fullJitter(initialDelay, multiplier, maxDelay));
        }

        /**
         * 设置决定异常是否可重试的条件。
         *
         * @param condition 返回 true 时重试该异常
         */
        public Builder<T> retryOnException(Predicate<? super Exception> condition) {
            this.exceptionCondition = ValidationUtils.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * 设置指定异常类型及其子类型可重试。
         *
         * @param type 可重试的异常类型
         */
        public Builder<T> retryOnException(Class<? extends Exception> type) {
            ValidationUtils.requireNonNull(type, "type");
            return retryOnException(type::isInstance);
        }

        /**
         * 设置决定成功结果是否仍需重试的条件。
         *
         * @param condition 返回 true 时继续重试
         */
        public Builder<T> retryOnResult(Predicate<? super T> condition) {
            this.resultCondition = ValidationUtils.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * 设置每次尝试完成后接收状态的监听器。
         *
         * @param listener 尝试状态监听器
         */
        public Builder<T> listener(RetryListener<T> listener) {
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
