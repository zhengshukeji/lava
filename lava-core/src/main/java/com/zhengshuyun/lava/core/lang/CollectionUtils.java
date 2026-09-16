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

import java.util.Collection;

/**
 * 提供空值安全的常用集合判断。
 *
 * @author Toint
 * @since 2026/9/7
 */
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断集合是否为 {@code null} 或不包含元素。
     *
     * @param collection 待判断的集合
     * @return 集合为 {@code null} 或不包含元素时返回 true
     */
    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否不为 {@code null} 且包含至少一个元素。
     *
     * @param collection 待判断的集合
     * @return 集合包含至少一个元素时返回 true
     */
    public static boolean isNotEmpty(@Nullable Collection<?> collection) {
        return !isEmpty(collection);
    }
}
