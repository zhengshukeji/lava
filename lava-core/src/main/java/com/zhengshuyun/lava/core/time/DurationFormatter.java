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

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 时长格式化器。
 * <p>
 * 不可变对象，通过 {@link Builder} 构建，同一实例可用同一配置反复格式化不同 Duration。
 * <p>
 * 示例：
 * <pre>{@code
 * DurationFormatter formatter = DurationFormatter.builder()
 *     .largestUnit(ChronoUnit.HOURS)
 *     .chinese()
 *     .build();
 *
 * formatter.format(Duration.ofSeconds(3661)); // "1时 1分 1秒"
 * formatter.format(Duration.ofSeconds(90));   // "1分 30秒"
 * }</pre>
 *
 * @author Toint
 * @since 2026/1/11
 */
public final class DurationFormatter {

    /**
     * 最大单位
     */
    private final ChronoUnit largestUnit;

    /**
     * 最小单位
     */
    private final ChronoUnit smallestUnit;

    /**
     * 语言环境
     */
    private final Locale locale;

    /**
     * 是否显示零值单位
     */
    private final boolean showZeroValues;

    /**
     * 单位之间的分隔符
     */
    private final String separator;

    private DurationFormatter(Builder builder) {
        this.largestUnit = builder.largestUnit;
        this.smallestUnit = builder.smallestUnit;
        this.locale = builder.locale;
        this.showZeroValues = builder.showZeroValues;
        this.separator = builder.separator;
    }

    /**
     * 创建 Builder 实例。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 格式化时长。
     *
     * <p>只支持天到纳秒的精确时间单位；刻意不接受月和年，因为它们的长度取决于具体日期和时区。
     *
     * @param duration 待格式化的时长（不能为 null 或负数）
     * @return 格式化后的字符串
     */
    public String format(Duration duration) {
        ValidationUtils.requireNonNull(duration, "duration cannot be null");
        ValidationUtils.requireFalse(duration.isNegative(), "duration cannot be negative");

        List<String> parts = new ArrayList<>();

        long totalSeconds = duration.toSeconds();

        long days = 0, hours = 0, minutes = 0, seconds = 0;
        long millis = 0, micros = 0, nanos = 0;

        if (shouldInclude(ChronoUnit.DAYS)) {
            days = totalSeconds / (24 * 3600);
            totalSeconds %= (24 * 3600);
        }

        if (shouldInclude(ChronoUnit.HOURS)) {
            hours = totalSeconds / 3600;
            totalSeconds %= 3600;
        }

        if (shouldInclude(ChronoUnit.MINUTES)) {
            minutes = totalSeconds / 60;
            totalSeconds %= 60;
        }

        if (shouldInclude(ChronoUnit.SECONDS)) {
            seconds = totalSeconds;
            totalSeconds = 0;
        }

        // 如果 SECONDS 不在范围内，需要将未消费的秒数转换为纳秒。
        // 用 multiplyExact 而非裸乘：当 largestUnit 为 MILLIS 及以下时 totalSeconds 是完整秒数,
        // 超过约 292 年就会溢出. 溢出时抛 ArithmeticException, 而不是静默返回错误结果
        long remainingNanos = Math.addExact(
                Math.multiplyExact(totalSeconds, 1_000_000_000L), duration.toNanosPart());

        if (shouldInclude(ChronoUnit.MILLIS)) {
            millis = remainingNanos / 1_000_000;
            remainingNanos %= 1_000_000;
        }

        if (shouldInclude(ChronoUnit.MICROS)) {
            micros = remainingNanos / 1_000;
            remainingNanos %= 1_000;
        }

        if (shouldInclude(ChronoUnit.NANOS)) {
            nanos = remainingNanos;
        }

        addPart(parts, days, ChronoUnit.DAYS);
        addPart(parts, hours, ChronoUnit.HOURS);
        addPart(parts, minutes, ChronoUnit.MINUTES);
        addPart(parts, seconds, ChronoUnit.SECONDS);
        addPart(parts, millis, ChronoUnit.MILLIS);
        addPart(parts, micros, ChronoUnit.MICROS);
        addPart(parts, nanos, ChronoUnit.NANOS);

        return parts.isEmpty() ? "0" + getUnitSuffix(smallestUnit) : String.join(separator, parts);
    }

    private boolean shouldInclude(ChronoUnit unit) {
        int unitOrder = getUnitOrder(unit);
        int largestOrder = getUnitOrder(largestUnit);
        int smallestOrder = getUnitOrder(smallestUnit);
        return unitOrder >= smallestOrder && unitOrder <= largestOrder;
    }

    private void addPart(List<String> parts, long value, ChronoUnit unit) {
        if (!shouldInclude(unit)) {
            return;
        }
        if (value > 0 || showZeroValues) {
            parts.add(value + getUnitSuffix(unit));
        }
    }

    private String getUnitSuffix(ChronoUnit unit) {
        if (locale.getLanguage().equals(Locale.CHINESE.getLanguage())) {
            return switch (unit) {
                case DAYS -> "天";
                case HOURS -> "小时";
                case MINUTES -> "分钟";
                case SECONDS -> "秒";
                case MILLIS -> "毫秒";
                case MICROS -> "微秒";
                case NANOS -> "纳秒";
                default -> "";
            };
        } else {
            return switch (unit) {
                case DAYS -> "d";
                case HOURS -> "h";
                case MINUTES -> "min";
                case SECONDS -> "s";
                case MILLIS -> "ms";
                case MICROS -> "μs";
                case NANOS -> "ns";
                default -> "";
            };
        }
    }

    private static int getUnitOrder(ChronoUnit unit) {
        return switch (unit) {
            case DAYS -> 7;
            case HOURS -> 6;
            case MINUTES -> 5;
            case SECONDS -> 4;
            case MILLIS -> 3;
            case MICROS -> 2;
            case NANOS -> 1;
            default -> 0;
        };
    }

    /**
     * 时长格式化器构建器。
     *
     * @author Toint
     * @since 2026/1/11
     */
    public static final class Builder {

        /**
         * 最大单位（默认为小时）
         */
        private ChronoUnit largestUnit = ChronoUnit.HOURS;

        /**
         * 最小单位（默认为秒）
         */
        private ChronoUnit smallestUnit = ChronoUnit.SECONDS;

        /**
         * 语言环境（默认为英文）
         */
        private Locale locale = Locale.ENGLISH;

        /**
         * 是否显示零值单位（默认为 false）
         */
        private boolean showZeroValues = false;

        /**
         * 单位之间的分隔符（默认为空格）
         */
        private String separator = " ";

        private Builder() {
        }

        /**
         * 设置最大单位。
         *
         * @param largestUnit 最大单位（DAYS/HOURS/MINUTES/SECONDS/MILLIS/MICROS/NANOS）
         * @return 当前构建器
         */
        public Builder largestUnit(ChronoUnit largestUnit) {
            this.largestUnit = ValidationUtils.requireNonNull(
                    largestUnit, "largestUnit cannot be null");
            return this;
        }

        /**
         * 设置最小单位。
         *
         * @param smallestUnit 最小单位（DAYS/HOURS/MINUTES/SECONDS/MILLIS/MICROS/NANOS）
         * @return 当前构建器
         */
        public Builder smallestUnit(ChronoUnit smallestUnit) {
            this.smallestUnit = ValidationUtils.requireNonNull(
                    smallestUnit, "smallestUnit cannot be null");
            return this;
        }

        /**
         * 设置单位范围。
         * <p>
         * 参数在此立即校验；单独调用 {@link #largestUnit} / {@link #smallestUnit}
         * 时无法在设值点判断区间方向，由 {@link #build()} 兜底校验。
         *
         * @param largestUnit  最大单位
         * @param smallestUnit 最小单位
         * @return 当前构建器
         * @throws IllegalArgumentException 如果 largestUnit &lt; smallestUnit 或单位不支持
         */
        public Builder range(ChronoUnit largestUnit, ChronoUnit smallestUnit) {
            largestUnit(largestUnit).smallestUnit(smallestUnit);
            validateRange();
            return this;
        }

        /**
         * 校验单位合法性与区间方向。
         *
         * @throws IllegalArgumentException 如果单位不受支持，或 largestUnit &lt; smallestUnit
         */
        private void validateRange() {
            int largestOrder = getUnitOrder(largestUnit);
            int smallestOrder = getUnitOrder(smallestUnit);

            // 不受支持的单位 order 为 0，会让 shouldInclude 的区间判断失去意义：
            // largestUnit 非法时所有单位都被排除（格式化结果恒为 "0"），
            // smallestUnit 非法时所有单位都被包含（整点时长会渲染出 "1h 5ns"）
            ValidationUtils.requireTrue(largestOrder > 0 && smallestOrder > 0,
                    "Unsupported unit: only DAYS/HOURS/MINUTES/SECONDS/MILLIS/MICROS/NANOS are supported");
            // 区间反向时没有任何单位落在范围内，时长会被静默丢弃
            ValidationUtils.requireTrue(largestOrder >= smallestOrder,
                    "largestUnit must be >= smallestUnit");
        }

        /**
         * 设置语言（中文、英文等）。
         *
         * @param locale 单位文本使用的区域设置
         * @return 当前构建器
         */
        public Builder locale(Locale locale) {
            this.locale = ValidationUtils.requireNonNull(locale, "locale cannot be null");
            return this;
        }

        /**
         * 单位文本使用中文。
         *
         * @return 当前构建器
         */
        public Builder chinese() {
            return locale(Locale.CHINESE);
        }

        /**
         * 单位文本使用英文。
         *
         * @return 当前构建器
         */
        public Builder english() {
            return locale(Locale.ENGLISH);
        }

        /**
         * 设置是否显示零值单位。
         *
         * @param showZeroValues true：如 "1h 0m 30s"；false：如 "1h 30s"
         * @return 当前构建器
         */
        public Builder showZeroValues(boolean showZeroValues) {
            this.showZeroValues = showZeroValues;
            return this;
        }

        /**
         * 设置单位之间的分隔符。
         *
         * @param separator 单位文本之间使用的分隔符
         * @return 当前构建器
         */
        public Builder separator(String separator) {
            this.separator = ValidationUtils.requireNonNull(
                    separator, "separator cannot be null");
            return this;
        }

        /**
         * 构建 DurationFormatter 实例。
         * <p>
         * 单位合法性与区间方向在此统一校验，因此无论通过 {@link #range} 还是单独
         * 调用 {@link #largestUnit} / {@link #smallestUnit} 都无法绕过。
         *
         * @return 不可变的 DurationFormatter 实例
         * @throws IllegalArgumentException 如果单位不受支持，或 largestUnit &lt; smallestUnit
         */
        public DurationFormatter build() {
            validateRange();
            return new DurationFormatter(this);
        }
    }
}
