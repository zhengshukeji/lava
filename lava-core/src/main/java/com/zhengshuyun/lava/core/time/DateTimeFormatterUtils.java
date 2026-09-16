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

package com.zhengshuyun.lava.core.time;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * 线程安全的常用日期时间格式化器，全部采用严格解析。
 */
public final class DateTimeFormatterUtils {

    /**
     * ISO 风格日期 {@code uuuu-MM-dd}，严格解析。
     */
    public static final DateTimeFormatter DATE = strict("uuuu-MM-dd");

    /**
     * 24 小时制时间 {@code HH:mm:ss}，严格解析。
     */
    public static final DateTimeFormatter TIME = strict("HH:mm:ss");

    /**
     * 空格分隔的日期时间 {@code uuuu-MM-dd HH:mm:ss}，严格解析。
     */
    public static final DateTimeFormatter DATE_TIME = strict("uuuu-MM-dd HH:mm:ss");

    /**
     * 带毫秒的日期时间 {@code uuuu-MM-dd HH:mm:ss.SSS}，严格解析。
     */
    public static final DateTimeFormatter DATE_TIME_MILLIS = strict("uuuu-MM-dd HH:mm:ss.SSS");

    /**
     * 紧凑日期 {@code uuuuMMdd}，严格解析。
     */
    public static final DateTimeFormatter COMPACT_DATE = strict("uuuuMMdd");

    /**
     * 紧凑日期时间 {@code uuuuMMddHHmmss}，严格解析。
     */
    public static final DateTimeFormatter COMPACT_DATE_TIME = strict("uuuuMMddHHmmss");

    /**
     * 斜杠分隔的日期 {@code uuuu/MM/dd}，严格解析。
     */
    public static final DateTimeFormatter SLASH_DATE = strict("uuuu/MM/dd");

    /**
     * 斜杠分隔的日期时间 {@code uuuu/MM/dd HH:mm:ss}，严格解析。
     */
    public static final DateTimeFormatter SLASH_DATE_TIME = strict("uuuu/MM/dd HH:mm:ss");

    private DateTimeFormatterUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
