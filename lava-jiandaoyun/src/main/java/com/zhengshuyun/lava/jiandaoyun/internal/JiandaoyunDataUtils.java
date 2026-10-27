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

package com.zhengshuyun.lava.jiandaoyun.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简道云数据写入值的包装工具。
 *
 * <p>简道云新建/修改数据接口要求每个字段写成 {@code {"字段名": {"value": 值}}}，子表单的每一行
 * （包括行 {@code _id}）也按同样规则包装。调用方只需传入 {@code 字段名 -> 值}，由本工具统一转换。</p>
 */
public final class JiandaoyunDataUtils {
    /** 简道云字段值的包装键。 */
    private static final String VALUE_KEY = "value";

    /** 禁止实例化数据包装工具。 */
    private JiandaoyunDataUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 复制数据中的 Map 和 List，固定请求内容并保留 null；其他值按原值保存。
     *
     * @param data 待复制的映射
     * @param <K> 映射键类型
     * @return 嵌套 Map 和 List 均不可修改的副本
     */
    public static <K> Map<K, @Nullable Object> immutableCopy(Map<K, ?> data) {
        ValidationUtils.requireNonNull(data, "data must not be null");
        Map<K, @Nullable Object> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 递归复制 JSON 集合，避免调用方复用子表单行或列表时改变已经构建的请求。
     *
     * @param value 原始值
     * @return 集合副本或原始标量值
     */
    private static @Nullable Object copyValue(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableCopy(map);
        }
        if (value instanceof List<?> list) {
            List<@Nullable Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyValue(item)));
            // List.copyOf 不允许 null，不能用于表达清空意图的数据。
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /**
     * 把 {@code 字段名 -> 值} 转换为简道云写入格式。
     *
     * <p>值为「元素全部是 Map 的 List」时视为子表单行，行内每个字段再包装一层；其余值（文本、数字、
     * 成员列表、地址对象等）原样放入 {@code value}。{@code null} 值保留，用于清空字段。</p>
     *
     * @param data 字段名到值的映射
     * @return 可直接序列化的写入结构
     */
    public static Map<String, Object> wrap(Map<String, ?> data) {
        ValidationUtils.requireNonNull(data, "data must not be null");
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach((field, value) -> result.put(
                JiandaoyunValidationUtils.requireNotBlank(field, "data field name"),
                wrapValue(isSubformRows(value) ? wrapRows((List<?>) value) : value)));
        return result;
    }

    /**
     * 批量转换多条数据。
     *
     * @param dataList 多条数据
     * @return 写入结构列表
     */
    public static List<Map<String, Object>> wrapAll(List<? extends Map<String, ?>> dataList) {
        ValidationUtils.requireNonNull(dataList, "dataList must not be null");
        List<Map<String, Object>> result = new ArrayList<>(dataList.size());
        dataList.forEach(data -> result.add(wrap(data)));
        return result;
    }

    /**
     * 包装子表单行：行内每个字段（含行 {@code _id}）都包成 {@code {"value": 值}}。
     *
     * @param rows 子表单行
     * @return 包装后的行列表
     */
    private static List<Map<String, Object>> wrapRows(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            ((Map<?, ?>) row).forEach((field, value) -> wrapped.put(String.valueOf(field), wrapValue(value)));
            result.add(wrapped);
        }
        return result;
    }

    /**
     * 判断值是否为子表单行列表。
     *
     * @param value 字段值
     * @return 非空且元素全部为 Map 时为 {@code true}
     */
    private static boolean isSubformRows(@Nullable Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        return list.stream().allMatch(Map.class::isInstance);
    }

    /**
     * 生成 {@code {"value": 值}}；使用可容纳 null 的 Map，以支持清空字段。
     *
     * @param value 字段值
     * @return 包装对象
     */
    private static Map<String, @Nullable Object> wrapValue(@Nullable Object value) {
        Map<String, @Nullable Object> wrapped = new LinkedHashMap<>(2);
        wrapped.put(VALUE_KEY, value);
        return wrapped;
    }
}
