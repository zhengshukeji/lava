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

package com.zhengshuyun.lava.core.id;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.time.Clock;
import java.time.Instant;

/**
 * 标准 64 位雪花算法 ID 生成器，工作节点标识由部署显式分配。
 *
 * <p>位布局：1 个未使用的符号位、41 位时间戳、10 位工作节点、12 位序列，Lava epoch 为
 * 2026-01-01T00:00:00Z。时钟回拨与单毫秒序列耗尽都会立即抛异常，让调用方第一时间感知并告警，
 * 而不是被隐式等待掩盖。
 *
 * <p>{@code workerId} 必须由部署配置分配，在所有实例间唯一且重启后保持稳定——两个实例复用
 * 同一个 {@code workerId} 会产出逐位相同的 ID 序列。本类刻意不提供默认值：10 位只有 1024 个
 * 取值，20 个实例按哈希分配的碰撞概率已达 17%，容器 IP 的低位熵更是远小于 10 位，任何自动推导
 * 都只是把启动期的显式失败换成运行期的静默重复 ID。
 */
public final class SnowflakeIdGenerator {

    /** Lava 纪元：雪花 ID 时间戳的起点（2026-01-01T00:00:00Z）。 */
    public static final Instant LAVA_EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    /** 工作节点标识的最小取值。 */
    public static final int MIN_WORKER_ID = 0;
    /** 工作节点标识的最大取值（10 位字段的自然上限）。 */
    public static final int MAX_WORKER_ID = 1023;

    private static final int WORKER_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_TIMESTAMP = (1L << 41) - 1;
    private static final long EPOCH_MILLIS = LAVA_EPOCH.toEpochMilli();

    private final int workerId;
    private final Clock clock;

    private long lastUnixMillis = Long.MIN_VALUE;
    private long sequence;

    /**
     * 创建生成器，工作节点标识须由部署配置显式分配。
     *
     * @param workerId 工作节点标识，取值 0 到 1023
     */
    public SnowflakeIdGenerator(int workerId) {
        this(workerId, Clock.systemUTC());
    }

    /**
     * 创建生成器并指定时钟，主要供确定性测试使用。
     *
     * @param workerId 工作节点标识，取值 0 到 1023
     * @param clock    读取当前时间的时钟
     */
    public SnowflakeIdGenerator(int workerId, Clock clock) {
        if (workerId < MIN_WORKER_ID || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and 1023: " + workerId);
        }
        this.workerId = workerId;
        this.clock = ValidationUtils.requireNonNull(clock, "clock");
    }

    /**
     * 返回下一个雪花 ID。
     *
     * @return 非负的雪花 ID
     */
    public synchronized long nextId() {
        long unixMillis = clock.millis();
        long relativeMillis = unixMillis - EPOCH_MILLIS;
        if (relativeMillis < 0 || relativeMillis > MAX_TIMESTAMP) {
            throw new IdGenerationException(
                    "Snowflake timestamp is outside the Lava epoch range: " + unixMillis);
        }

        if (lastUnixMillis != Long.MIN_VALUE && unixMillis < lastUnixMillis) {
            throw new IdGenerationException(
                    "Clock moved backwards by " + (lastUnixMillis - unixMillis) + " ms");
        }

        if (unixMillis == lastUnixMillis) {
            if (sequence == MAX_SEQUENCE) {
                throw new IdGenerationException(
                        "Snowflake sequence exhausted at Unix millisecond " + unixMillis);
            }
            sequence++;
        } else {
            lastUnixMillis = unixMillis;
            sequence = 0;
        }

        return (relativeMillis << (WORKER_BITS + SEQUENCE_BITS))
                | ((long) workerId << SEQUENCE_BITS)
                | sequence;
    }

    /**
     * 返回 {@link #nextId()} 的十进制字符串，字符串形式可避开 JSON/JS 侧的 53 位精度截断。
     *
     * @return 新雪花 ID 的十进制字符串
     */
    public String nextIdString() {
        return Long.toString(nextId());
    }

    /**
     * 返回本生成器的工作节点标识。
     *
     * @return 工作节点标识
     */
    public int workerId() {
        return workerId;
    }

    /**
     * 从雪花 ID 还原生成时刻（要求该 ID 由 Lava epoch 位布局生成）。
     *
     * @param id 非负雪花 ID
     * @return ID 内嵌的 UTC 时间戳
     */
    public static Instant timestamp(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        long relativeMillis = id >>> (WORKER_BITS + SEQUENCE_BITS);
        return Instant.ofEpochMilli(EPOCH_MILLIS + relativeMillis);
    }
}
