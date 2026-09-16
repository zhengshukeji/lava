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

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * 时间工具类
 *
 * @author Toint
 * @since 2026/01/17
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /**
     * 时间部分: {@code HH:mm:ss}, 可选 1~9 位小数秒
     * <p>
     * 必须在 {@link #DASH} / {@link #SLASH} 之前声明，静态初始化按声明顺序执行
     */
    private static final DateTimeFormatter TIME_PART = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    /**
     * 横杠分隔: {@code yyyy-MM-dd}, 可选 {@code (空格|T)HH:mm:ss}, 可选小数秒
     * <p>
     * 模式里用 {@code uuuu} (纪年年份) 而非 {@code yyyy} (纪元内年份),
     * 因为 STRICT 解析下 {@code yyyy} 会强制要求纪元字段
     */
    private static final DateTimeFormatter DASH = dateTimeFormatter("uuuu-MM-dd");

    /**
     * 斜杠分隔: {@code yyyy/MM/dd}, 可选时间部分
     */
    private static final DateTimeFormatter SLASH = dateTimeFormatter("uuuu/MM/dd");

    /**
     * 紧凑格式: {@code yyyyMMdd}, 可选 {@code HHmmss}
     */
    private static final DateTimeFormatter COMPACT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .optionalStart()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 中文格式: {@code yyyy年MM月dd日}, 可选 {@code [空格]HH时mm分ss秒}
     */
    private static final DateTimeFormatter CHINESE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('年')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('月')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('日')
            .optionalStart()
            // "年月日时分秒"本身即字面量分隔，字段边界不依赖空格，因此空格可省略;
            // 与横杠/斜杠格式不同 (数字直接相连会产生歧义，必须强制分隔符)
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral('时')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral('分')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral('秒')
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 构建"日期 + 可选时间"的格式化器
     * <p>
     * 日期与时间之间允许空格或 {@code T} 分隔，秒后允许 1~9 位小数秒。
     * 缺失时间部分时默认为 00:00:00
     *
     * @param datePattern 日期部分的模式
     * @return 格式化器
     */
    private static DateTimeFormatter dateTimeFormatter(String datePattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(datePattern)
                // 两个互斥的可选分支，每个分支都把"分隔符 + 时间"绑在一起,
                // 因此分隔符必须且只能出现一个. 若把分隔符各自放进独立的 optional 段,
                // 两者都可跳过，会连带接受 "2026-01-0112:30:00" (无分隔符) 和
                // "2026-01-01 T12:30:00" (两个分隔符)
                .optionalStart().appendLiteral(' ').append(TIME_PART).optionalEnd()
                .optionalStart().appendLiteral('T').append(TIME_PART).optionalEnd()
                // 只有日期时补齐时间部分，使其可直接解析为 LocalDateTime
                // parseDefaulting 仅在字段未被解析到时生效，因此不会与显式时间冲突
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter()
                // STRICT: 拒绝 2026-02-30 这类不存在的日期，而不是按 SMART 悄悄夹到 02-28
                .withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * 把字符串解析为 LocalDateTime，支持多种常见格式。
     * <p>
     * 支持的格式 (按优先级顺序):
     * <ul>
     *   <li>{@code yyyy-MM-dd HH:mm:ss} - 标准日期时间，如: 2026-01-01 12:30:00</li>
     *   <li>{@code yyyy-MM-dd HH:mm:ss.SSS} - 带毫秒的标准日期时间，如: 2026-01-01 12:30:00.123</li>
     *   <li>{@code yyyy/MM/dd HH:mm:ss} - 斜杠分隔日期时间，如: 2026/01/01 12:30:00</li>
     *   <li>{@code yyyy/MM/dd HH:mm:ss.SSS} - 带毫秒的斜杠分隔日期时间，如: 2026/01/01 12:30:00.123</li>
     *   <li>{@code yyyyMMddHHmmss} - 紧凑日期时间，如: 20260101123000</li>
     *   <li>{@code yyyy年MM月dd日[空格]HH时mm分ss秒} - 中文日期时间，如: 2026年01月01日 12时30分00秒,
     *       "日"与"时"之间的空格可省略</li>
     *   <li>{@code yyyy-MM-dd'T'HH:mm:ss} - ISO 8601 标准本地时间，如: 2026-01-01T12:30:00</li>
     *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSS} - 带毫秒的 ISO 8601 标准本地时间，如: 2026-01-01T12:30:00.123</li>
     *   <li>{@code yyyy-MM-dd} - 标准日期，如: 2026-01-01 (时间部分默认为 00:00:00)</li>
     *   <li>{@code yyyy/MM/dd} - 斜杠分隔日期，如: 2026/01/01 (时间部分默认为 00:00:00)</li>
     *   <li>{@code yyyyMMdd} - 紧凑日期，如: 20260101 (时间部分默认为 00:00:00)</li>
     *   <li>{@code yyyy年MM月dd日} - 中文日期，如: 2026年01月01日 (时间部分默认为 00:00:00)</li>
     * </ul>
     *
     * <h4>解析规则</h4>
     * <ul>
     *   <li>输入前后空白会被裁剪</li>
     *   <li>小数秒接受 1~9 位，如 {@code .1} / {@code .123} / {@code .123456789}</li>
     *   <li>横杠/斜杠格式的日期与时间之间必须且只能有一个分隔符 (空格或 {@code T});
     *       中文格式的"日"与"时"之间空格可省略 (中文单位本身即分隔，无歧义)</li>
     *   <li>采用严格解析: {@code 2026-02-30} 这类不存在的日期返回 null,
     *   不会被悄悄修正为月末</li>
     *   <li>月、日、时、分、秒必须补零到两位，{@code 2026-1-1} 无法解析</li>
     * </ul>
     *
     * @param dateTime 日期时间字符串
     * @return LocalDateTime 对象，解析失败或输入为空时返回 null
     */
    public static @Nullable LocalDateTime parse(@Nullable String dateTime) {
        String text = dateTime == null ? "" : dateTime.trim();

        if (text.isEmpty()) {
            return null;
        }

        // 按分隔符选出唯一候选格式，避免逐个 formatter 试错时抛出并丢弃大量 DateTimeParseException
        DateTimeFormatter formatter;
        if (text.indexOf('-') > 0) {
            formatter = DASH;
        } else if (text.indexOf('/') > 0) {
            formatter = SLASH;
        } else if (text.indexOf('年') > 0) {
            formatter = CHINESE;
        } else {
            formatter = COMPACT;
        }

        try {
            return LocalDateTime.parse(text, formatter);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }
}
