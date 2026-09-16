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

/**
 * 生成器进入无法继续产出 ID 的异常状态时抛出，例如时钟回拨、序列耗尽或时间戳越界。
 */
public final class IdGenerationException extends IllegalStateException {

    /**
     * 创建携带失败原因的异常。
     *
     * @param message ID 生成失败的具体原因
     */
    public IdGenerationException(String message) {
        super(message);
    }
}
