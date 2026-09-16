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

package com.zhengshuyun.lava.core.lang;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 提供空值安全的常用映射判断。
 *
 * @author Toint
 * @since 2026/9/7
 */
public final class MapUtils {

    private MapUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断映射是否为 {@code null} 或不包含条目。
     *
     * @param map 待判断的映射
     * @return 映射为 {@code null} 或不包含条目时返回 true
     */
    public static boolean isEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断映射是否不为 {@code null} 且包含至少一个条目。
     *
     * @param map 待判断的映射
     * @return 映射包含至少一个条目时返回 true
     */
    public static boolean isNotEmpty(@Nullable Map<?, ?> map) {
        return !isEmpty(map);
    }
}
