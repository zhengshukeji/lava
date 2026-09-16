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

package com.zhengshuyun.lava.json;

/**
 * 表示 JSON 编码、解码或转换失败。
 */
public final class JsonException extends RuntimeException {

    /**
     * 创建携带错误消息与根因的异常。
     *
     * @param message 错误消息
     * @param cause   失败根因
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建携带错误消息的异常。
     *
     * @param message 错误消息
     */
    public JsonException(String message) {
        super(message);
    }
}
