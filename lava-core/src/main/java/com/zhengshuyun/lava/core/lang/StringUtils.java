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

/**
 * 提供空值安全的常用字符串判断和默认值转换。
 *
 * @author Toint
 * @since 2026/9/7
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断字符串是否为 {@code null} 或空字符串。
     *
     * @param value 待判断的字符串
     * @return 为 {@code null} 或长度为零时返回 true
     */
    public static boolean isEmpty(@Nullable String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 判断字符串是否既不为 {@code null} 也不为空字符串。
     *
     * @param value 待判断的字符串
     * @return 包含至少一个字符时返回 true
     */
    public static boolean isNotEmpty(@Nullable String value) {
        return !isEmpty(value);
    }

    /**
     * 判断字符串是否为 {@code null}、空字符串或仅包含 Unicode 空白字符。
     *
     * @param value 待判断的字符串
     * @return 没有非空白字符时返回 true
     */
    public static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断字符串是否包含至少一个非空白字符。
     *
     * @param value 待判断的字符串
     * @return 既非 {@code null} 且包含非空白字符时返回 true
     */
    public static boolean isNotBlank(@Nullable String value) {
        return !isBlank(value);
    }

    /**
     * 将 {@code null} 转为空字符串，非空值保持不变。
     *
     * @param value 待转换的字符串
     * @return 非 {@code null} 的原值或空字符串
     */
    public static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /**
     * 将空字符串转为 {@code null}，其他值保持不变。
     *
     * @param value 待转换的字符串
     * @return 输入为空字符串时返回 {@code null}，否则返回原值
     */
    public static @Nullable String emptyToNull(@Nullable String value) {
        return isEmpty(value) ? null : value;
    }

    /**
     * 输入为 {@code null} 或空字符串时返回默认值，其他值保持不变。
     *
     * @param value        待判断的字符串
     * @param defaultValue 非空默认值
     * @return 输入为空时返回默认值，否则返回原值
     * @throws IllegalArgumentException 默认值为 {@code null} 时抛出
     */
    public static String defaultIfEmpty(@Nullable String value, String defaultValue) {
        ValidationUtils.requireNonNull(defaultValue, "defaultValue must not be null");
        return isEmpty(value) ? defaultValue : value;
    }

    /**
     * 输入为 {@code null}、空字符串或纯空白时返回默认值，其他值保持不变。
     *
     * @param value        待判断的字符串
     * @param defaultValue 非空默认值
     * @return 输入为空白时返回默认值，否则返回原值
     * @throws IllegalArgumentException 默认值为 {@code null} 时抛出
     */
    public static String defaultIfBlank(@Nullable String value, String defaultValue) {
        ValidationUtils.requireNonNull(defaultValue, "defaultValue must not be null");
        return isBlank(value) ? defaultValue : value;
    }
}
