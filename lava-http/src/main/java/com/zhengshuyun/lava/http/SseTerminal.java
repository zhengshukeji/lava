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
 * SSE 会话的唯一终态。
 *
 * @param termination 会话终止原因
 * @param failure     失败详情；非失败终态时为 null
 */
public record SseTerminal(SseTermination termination, @Nullable SseFailure failure) {
    /**
     * 紧凑构造器：校验终止原因非空，且失败详情仅在 FAILED 终态出现。
     */
    public SseTerminal {
        ValidationUtils.requireNonNull(termination, "termination must not be null");
        if ((termination == SseTermination.FAILED) != (failure != null)) {
            throw new IllegalArgumentException("failure must be present exactly for FAILED");
        }
    }
}
