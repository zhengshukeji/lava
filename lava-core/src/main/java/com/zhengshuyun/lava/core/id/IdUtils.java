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

import java.util.UUID;

/**
 * UUID 与雪花算法 ID 的便捷入口。
 */
public final class IdUtils {

    /**
     * 进程内复用的 UUIDv7 生成器，持有跨调用保持的单调序列状态。
     */
    private static final UUIDv7Generator DEFAULT_UUID_V7_GENERATOR = new UUIDv7Generator();

    private IdUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 使用 JDK 生成随机 UUIDv4。
     *
     * @return 新的 UUIDv4
     */
    public static UUID nextUUID() {
        return UUID.randomUUID();
    }

    /**
     * 返回 {@link #nextUUID()} 的规范小写字符串。
     *
     * @return 含连字符的 UUIDv4 字符串
     */
    public static String nextUUIDString() {
        return nextUUID().toString();
    }

    /**
     * 返回不含连字符的 32 位小写 UUIDv4 字符串。
     *
     * @return 不含连字符的 UUIDv4 字符串
     */
    public static String nextUUIDStringWithoutHyphens() {
        return nextUUIDString().replace("-", "");
    }

    /**
     * 返回进程内下一个单调递增的 UUIDv7。
     *
     * @return 新的 UUIDv7
     */
    public static UUID nextUUIDv7() {
        return DEFAULT_UUID_V7_GENERATOR.next();
    }

    /**
     * 返回 {@link #nextUUIDv7()} 的规范小写字符串。
     *
     * @return 含连字符的 UUIDv7 字符串
     */
    public static String nextUUIDv7String() {
        return nextUUIDv7().toString();
    }

    /**
     * 返回不含连字符的 32 位小写 UUIDv7 字符串。
     *
     * @return 不含连字符的 UUIDv7 字符串
     */
    public static String nextUUIDv7StringWithoutHyphens() {
        return nextUUIDv7String().replace("-", "");
    }

    /**
     * 创建雪花算法生成器，工作节点标识由部署显式分配。
     *
     * <p>生成器必须在进程内复用：每次生成 ID 都新建实例会不断重置序列状态。
     *
     * @param workerId 部署配置分配的工作节点标识，取值 0 到 1023
     * @return 新的雪花算法生成器
     */
    public static SnowflakeIdGenerator newSnowflakeGenerator(int workerId) {
        return new SnowflakeIdGenerator(workerId);
    }

}
