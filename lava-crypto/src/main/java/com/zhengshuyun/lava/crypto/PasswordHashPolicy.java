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


package com.zhengshuyun.lava.crypto;

/**
 * 不可变的 Argon2id 参数。
 *
 * <p>默认值 {@link #DEFAULT} 为 64 MiB 内存、3 次迭代、1 个并行通道、16 字节盐、32 字节哈希，
 * 高于 OWASP 密码存储建议的 Argon2id 最低配置。</p>
 *
 * @param memoryKiB       内存大小，单位 KiB，至少为并行通道数的 8 倍
 * @param iterations      迭代次数
 * @param parallelism     并行通道数
 * @param saltLengthBytes 随机盐字节数，至少 8
 * @param hashLengthBytes 输出哈希字节数，至少 4
 */
public record PasswordHashPolicy(
        int memoryKiB,
        int iterations,
        int parallelism,
        int saltLengthBytes,
        int hashLengthBytes) {

    /**
     * 默认策略。
     */
    public static final PasswordHashPolicy DEFAULT = new PasswordHashPolicy(65_536, 3, 1, 16, 32);

    /**
     * 校验 Argon2 规范要求的参数下限（RFC 9106 第 3.1 节）。
     */
    public PasswordHashPolicy {
        if (iterations < 1 || parallelism < 1) {
            throw new IllegalArgumentException("iterations and parallelism must be positive");
        }
        if (memoryKiB < 8L * parallelism) {
            throw new IllegalArgumentException("memoryKiB must be at least eight times parallelism");
        }
        if (saltLengthBytes < 8) {
            throw new IllegalArgumentException("saltLengthBytes must be at least 8");
        }
        if (hashLengthBytes < 4) {
            throw new IllegalArgumentException("hashLengthBytes must be at least 4");
        }
    }
}
