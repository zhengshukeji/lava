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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * 按 Lava 默认约定创建不可变的 Jackson 3 mapper。
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按默认约定创建 mapper：{@link Locale#ROOT}，不注册全局自定义序列化器，
     * long 值保持为 JSON number。
     */
    public static ObjectMapper defaultMapper() {
        return builder().build();
    }

    /**
     * 创建 mapper 配置构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * builder 本身无共享状态；{@link #build()} 返回的 mapper 不可变且线程安全。
     */
    public static final class Builder {

        /**
         * {@link LocalDateTime} 的可选格式模式。
         */
        private @Nullable String dateTimePattern;

        /**
         * {@link LocalDate} 的可选格式模式。
         */
        private @Nullable String datePattern;

        /**
         * {@link LocalTime} 的可选格式模式。
         */
        private @Nullable String timePattern;

        /**
         * 绝对时间类型的可选默认时区。
         */
        private @Nullable ZoneId zone;

        /**
         * 日期时间格式化使用的区域设置，默认为根区域。
         */
        private Locale locale = Locale.ROOT;

        /**
         * 按注册顺序加入 mapper 的 Jackson 模块。
         */
        private final List<JacksonModule> modules = new ArrayList<>();

        /**
         * 对底层 Jackson Builder 的可选自定义配置回调。
         */
        private @Nullable Consumer<JsonMapper.Builder> customizer;

        private Builder() {
        }

        /**
         * 设置 {@link LocalDateTime} 的序列化和反序列化格式。
         *
         * @param pattern 非空白的日期时间格式
         * @return 当前构建器
         */
        public Builder localDateTimePattern(String pattern) {
            dateTimePattern = requirePattern(pattern, "pattern");
            return this;
        }

        /**
         * 设置 {@link LocalDate} 的序列化和反序列化格式。
         *
         * @param pattern 非空白的日期格式
         * @return 当前构建器
         */
        public Builder localDatePattern(String pattern) {
            datePattern = requirePattern(pattern, "pattern");
            return this;
        }

        /**
         * 设置 {@link LocalTime} 的序列化和反序列化格式。
         *
         * @param pattern 非空白的时间格式
         * @return 当前构建器
         */
        public Builder localTimePattern(String pattern) {
            timePattern = requirePattern(pattern, "pattern");
            return this;
        }

        /**
         * 设置绝对时间类型的默认时区；本地日期时间值不带时区。
         *
         * @param zone 默认时区
         * @return 当前构建器
         */
        public Builder zone(ZoneId zone) {
            this.zone = ValidationUtils.requireNonNull(zone, "zone");
            return this;
        }

        /**
         * 设置格式化和解析使用的区域设置。
         *
         * @param locale 区域设置
         * @return 当前构建器
         */
        public Builder locale(Locale locale) {
            this.locale = ValidationUtils.requireNonNull(locale, "locale");
            return this;
        }

        /**
         * 注册一个 Jackson 模块。
         *
         * @param module 待注册的模块
         * @return 当前构建器
         */
        public Builder addModule(JacksonModule module) {
            modules.add(ValidationUtils.requireNonNull(module, "module"));
            return this;
        }

        /**
         * 配置本工厂未覆盖到的 Jackson 能力。仅可设置一次，重复调用会替换先前回调。
         *
         * @param customizer 对底层 Jackson 构建器的配置回调
         * @return 当前构建器
         */
        public Builder customize(Consumer<JsonMapper.Builder> customizer) {
            this.customizer = ValidationUtils.requireNonNull(customizer, "customizer");
            return this;
        }

        /**
         * 根据当前配置创建不可变且线程安全的 mapper。
         *
         * @return 新的 mapper
         */
        public ObjectMapper build() {
            JsonMapper.Builder builder = JsonMapper.builder().defaultLocale(locale);
            applyPattern(builder, LocalDateTime.class, dateTimePattern);
            applyPattern(builder, LocalDate.class, datePattern);
            applyPattern(builder, LocalTime.class, timePattern);
            if (zone != null) {
                builder.defaultTimeZone(TimeZone.getTimeZone(zone));
            }
            builder.addModules(List.copyOf(modules));
            if (customizer != null) {
                customizer.accept(builder);
            }
            return builder.build();
        }

        private static String requirePattern(String pattern, String name) {
            return ValidationUtils.requireNotBlank(pattern, name + " must not be blank");
        }

        private static void applyPattern(
                JsonMapper.Builder builder, Class<?> type, @Nullable String pattern) {
            if (pattern != null) {
                builder.withConfigOverride(
                        type,
                        override -> override.setFormat(JsonFormat.Value.forPattern(pattern)));
            }
        }
    }
}
