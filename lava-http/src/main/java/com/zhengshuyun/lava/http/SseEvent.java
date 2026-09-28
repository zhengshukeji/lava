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

package com.zhengshuyun.lava.http;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 一条 SSE 事件。
 *
 * @param id   服务端事件 ID；未提供时为 null
 * @param type 事件类型；空白值归一化为 {@link #DEFAULT_TYPE}
 * @param data 事件数据
 */
public record SseEvent(@Nullable String id, String type, String data) {
    /**
     * SSE 规范约定的默认事件类型。
     */
    public static final String DEFAULT_TYPE = "message";

    /**
     * 紧凑构造器：空白事件类型归一化为默认类型，并校验数据非空。
     */
    public SseEvent {
        if (type == null || type.isBlank()) {
            type = DEFAULT_TYPE;
        }
        ValidationUtils.requireNonNull(data, "data must not be null");
    }

    /**
     * 判断该事件是否使用默认类型。
     *
     * @return 类型为 {@code message} 时返回 true
     */
    public boolean isDefaultType() {
        return DEFAULT_TYPE.equals(type);
    }
}
