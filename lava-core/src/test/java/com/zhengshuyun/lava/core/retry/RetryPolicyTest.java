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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void retriesCheckedExceptionsAndRethrowsTheOriginalInstance() {
        IOException expected = new IOException("offline");
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(3)
                .retryOnException(IOException.class)
                .build();

        IOException actual = assertThrows(IOException.class, () -> policy.call(() -> {
            calls.incrementAndGet();
            throw expected;
        }));

        assertSame(expected, actual);
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryExceptionsRejectedByCondition() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(3)
                .retryOnException(IOException.class)
                .build();

        assertThrows(IllegalStateException.class, () -> policy.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bug");
        }));

        assertEquals(1, calls.get());
    }

    @Test
    void doesNotEvaluateExceptionConditionAfterFinalAttempt() {
        IOException expected = new IOException("offline");
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(1)
                .retryOnException(failure -> {
                    throw new AssertionError("terminal condition must not be evaluated");
                })
                .build();

        assertSame(expected, assertThrows(IOException.class, () -> policy.call(() -> {
            throw expected;
        })));
        assertSame(expected, assertThrows(IOException.class, () -> policy.run(() -> {
            throw expected;
        })));
    }

    @Test
    void doesNotEvaluateResultConditionAfterFinalAttempt() throws Exception {
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(1)
                .retryOnResult(result -> {
                    throw new AssertionError("terminal condition must not be evaluated");
                })
                .build();

        assertEquals("ready", policy.call(() -> "ready"));
    }

    @Test
    void retriesOnResultAndReportsEveryAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<RetryAttempt<String>> attempts = new ArrayList<>();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(4)
                .fixedDelay(Duration.ofMillis(1))
                .retryOnResult("pending"::equals)
                .listener(attempts::add)
                .build();

        String result = policy.call(() -> calls.incrementAndGet() < 3 ? "pending" : "ready");

        assertEquals("ready", result);
        assertEquals(3, attempts.size());
        assertTrue(attempts.getFirst().willRetry());
        assertEquals(Duration.ofMillis(1), attempts.getFirst().nextDelay());
        assertFalse(attempts.getLast().willRetry());
        assertEquals(Duration.ZERO, attempts.getLast().nextDelay());
    }

    @Test
    void computesCappedExponentialAndFullJitterDelays() {
        RetryDelayStrategy exponential = RetryDelayStrategy.exponential(
                Duration.ofMillis(10), 2, Duration.ofMillis(25));
        assertEquals(Duration.ofMillis(10), exponential.delayAfter(1));
        assertEquals(Duration.ofMillis(20), exponential.delayAfter(2));
        assertEquals(Duration.ofMillis(25), exponential.delayAfter(3));

        RetryDelayStrategy jitter = RetryDelayStrategy.fullJitter(
                Duration.ofMillis(100), 2, Duration.ofSeconds(1));
        for (int attempt = 1; attempt <= 20; attempt++) {
            Duration cap = Duration.ofMillis(Math.min(100L << Math.min(attempt - 1, 4), 1000L));
            Duration delay = jitter.delayAfter(attempt);
            assertFalse(delay.isNegative());
            assertTrue(delay.compareTo(cap) < 0);
        }
    }

    @Test
    void interruptionIsNeverRetriedAndInterruptFlagIsRestored() {
        AtomicInteger calls = new AtomicInteger();
        List<RetryAttempt<String>> attempts = new ArrayList<>();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(5)
                .listener(attempts::add)
                .build();

        assertThrows(InterruptedException.class, () -> policy.call(() -> {
            calls.incrementAndGet();
            throw new InterruptedException("cancelled");
        }));

        assertEquals(1, calls.get());
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(1, attempts.size());
        assertInstanceOf(InterruptedException.class, attempts.getFirst().failure());
        assertFalse(attempts.getFirst().willRetry());
    }

    @Test
    void interruptionDuringDelayRestoresInterruptFlag() {
        // 监听器在等待前触发，借此在进入休眠前打上中断标记
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(2)
                .fixedDelay(Duration.ofSeconds(10))
                .listener(attempt -> Thread.currentThread().interrupt())
                .build();

        assertThrows(InterruptedException.class, () -> policy.call(() -> {
            throw new IOException("retryable");
        }));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void worksFromVirtualThreadsAndSupportsCheckedRunnable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<Void> policy = RetryPolicy.<Void>builder().maxAttempts(2).build();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                policy.run(() -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IOException("retry");
                    }
                });
                return null;
            }).get();
        }

        assertEquals(2, attempts.get());
    }
}
