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

package com.zhengshuyun.lava.jiandaoyun.data;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * 数据查询的单个过滤条件，通过静态工厂创建，例如 {@code DataCondition.eq("_widget_1", "张三")}。
 *
 * @param field 字段名
 * @param method 过滤方法，如 eq、in、range
 * @param value 过滤值；empty、not_empty 等无值方法为 {@code null}
 */
public record DataCondition(String field, String method, @Nullable List<@Nullable Object> value) {
    /**
     * 校验字段名和过滤方法。
     *
     * @param field 字段名
     * @param method 过滤方法
     * @param value 过滤值
     */
    public DataCondition {
        JiandaoyunValidationUtils.requireNotBlank(field, "field");
        JiandaoyunValidationUtils.requireNotBlank(method, "method");
    }

    /**
     * 等于。
     *
     * @param field 字段名
     * @param value 比较值
     * @return 过滤条件
     */
    public static DataCondition eq(String field, Object value) {
        return of(field, "eq", value);
    }

    /**
     * 不等于。
     *
     * @param field 字段名
     * @param value 比较值
     * @return 过滤条件
     */
    public static DataCondition ne(String field, Object value) {
        return of(field, "ne", value);
    }

    /**
     * 等于任意一个值。
     *
     * @param field 字段名
     * @param values 候选值
     * @return 过滤条件
     */
    public static DataCondition in(String field, Object... values) {
        return of(field, "in", values);
    }

    /**
     * 等于列表中任意一个值。
     *
     * @param field 字段名
     * @param values 候选值
     * @return 过滤条件
     */
    public static DataCondition in(String field, List<?> values) {
        return in(field, requireList(values).toArray());
    }

    /**
     * 不等于任意一个值。
     *
     * @param field 字段名
     * @param values 排除值
     * @return 过滤条件
     */
    public static DataCondition nin(String field, Object... values) {
        return of(field, "nin", values);
    }

    /**
     * 不等于列表中任意一个值。
     *
     * @param field 字段名
     * @param values 排除值
     * @return 过滤条件
     */
    public static DataCondition nin(String field, List<?> values) {
        return nin(field, requireList(values).toArray());
    }

    /**
     * 在闭区间内，适用于数字和日期；任一端为 {@code null} 表示不限。
     *
     * @param field 字段名
     * @param from 下限
     * @param to 上限
     * @return 过滤条件
     */
    public static DataCondition range(String field, @Nullable Object from, @Nullable Object to) {
        return new DataCondition(field, "range", Arrays.asList(from, to));
    }

    /**
     * 文本包含。
     *
     * @param field 字段名
     * @param value 包含的文本
     * @return 过滤条件
     */
    public static DataCondition like(String field, String value) {
        return of(field, "like", value);
    }

    /**
     * 大于。
     *
     * @param field 字段名
     * @param value 比较值
     * @return 过滤条件
     */
    public static DataCondition gt(String field, Object value) {
        return of(field, "gt", value);
    }

    /**
     * 小于。
     *
     * @param field 字段名
     * @param value 比较值
     * @return 过滤条件
     */
    public static DataCondition lt(String field, Object value) {
        return of(field, "lt", value);
    }

    /**
     * 为空。
     *
     * @param field 字段名
     * @return 过滤条件
     */
    public static DataCondition empty(String field) {
        return new DataCondition(field, "empty", null);
    }

    /**
     * 不为空。
     *
     * @param field 字段名
     * @return 过滤条件
     */
    public static DataCondition notEmpty(String field) {
        return new DataCondition(field, "not_empty", null);
    }

    /**
     * 手机号已验证。
     *
     * @param field 手机号字段名
     * @return 过滤条件
     */
    public static DataCondition verified(String field) {
        return new DataCondition(field, "verified", null);
    }

    /**
     * 手机号未验证。
     *
     * @param field 手机号字段名
     * @return 过滤条件
     */
    public static DataCondition unverified(String field) {
        return new DataCondition(field, "unverified", null);
    }

    /**
     * 同时包含全部值，适用于多选类字段。
     *
     * @param field 字段名
     * @param values 必须全部包含的值
     * @return 过滤条件
     */
    public static DataCondition all(String field, Object... values) {
        return of(field, "all", values);
    }

    /**
     * 同时包含列表中的全部值，适用于多选类字段。
     *
     * @param field 字段名
     * @param values 必须全部包含的值
     * @return 过滤条件
     */
    public static DataCondition all(String field, List<?> values) {
        return all(field, requireList(values).toArray());
    }

    /**
     * 创建带值的条件；可变参数的值数组会被复制为列表。
     *
     * @param field 字段名
     * @param method 过滤方法
     * @param values 过滤值
     * @return 过滤条件
     */
    private static DataCondition of(String field, String method, Object... values) {
        ValidationUtils.requireNonNull(values, "values must not be null");
        return new DataCondition(field, method, Arrays.asList(values.clone()));
    }

    /**
     * 校验列表参数非 null。
     *
     * @param values 列表
     * @return 原列表
     */
    private static List<?> requireList(List<?> values) {
        return ValidationUtils.requireNonNull(values, "values must not be null");
    }
}
