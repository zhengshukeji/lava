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

import java.util.*;

/**
 * 不可变且保持插入顺序的 HTTP 请求头。
 *
 * <p>请求头名称按大小写不敏感规则查找；名称和值都会在到达传输层前校验。
 * 请求头值有意只接受可见 ASCII 字符和水平制表符；这可拒绝 CR/LF 注入、控制字符
 * 以及含义不明确的非 ASCII 线缆编码。</p>
 */
public final class HttpHeaders {

    private static final HttpHeaders EMPTY = new HttpHeaders(List.of());

    /**
     * 交替存放的名称和值条目。
     */
    private final List<String> namesAndValues;

    private HttpHeaders(List<String> namesAndValues) {
        this.namesAndValues = List.copyOf(namesAndValues);
    }

    /**
     * 返回不含任何请求头的空实例。
     *
     * @return 空的请求头实例
     */
    public static HttpHeaders of() {
        return EMPTY;
    }

    /**
     * 以名称/值交替排列的序列创建请求头。
     *
     * @param namesAndValues 交替排列的请求头名称和值，长度必须为偶数
     * @return 新的请求头实例
     */
    public static HttpHeaders of(String... namesAndValues) {
        ValidationUtils.requireNonNull(namesAndValues, "namesAndValues must not be null");
        if ((namesAndValues.length & 1) != 0) {
            throw new IllegalArgumentException("namesAndValues must contain name/value pairs");
        }
        Builder builder = builder();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            builder.add(namesAndValues[index], namesAndValues[index + 1]);
        }
        return builder.build();
    }

    /**
     * 创建构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 按大小写不敏感规则查找首个同名请求头的值。
     *
     * @param name 请求头名称
     * @return 首个匹配的值，不存在时返回 {@code null}
     */
    public @Nullable String get(String name) {
        requireName(name);
        for (int index = namesAndValues.size() - 2; index >= 0; index -= 2) {
            if (namesAndValues.get(index).equalsIgnoreCase(name)) {
                return namesAndValues.get(index + 1);
            }
        }
        return null;
    }

    /**
     * 按大小写不敏感规则返回同名请求头的全部值。
     *
     * @param name 请求头名称
     * @return 按插入顺序排列的全部匹配值，可能为空列表
     */
    public List<String> values(String name) {
        requireName(name);
        List<String> result = new ArrayList<>();
        for (int index = 0; index < namesAndValues.size(); index += 2) {
            if (namesAndValues.get(index).equalsIgnoreCase(name)) {
                result.add(namesAndValues.get(index + 1));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 判断是否存在指定名称的请求头。
     *
     * @param name 请求头名称
     * @return 存在同名请求头时返回 true
     */
    public boolean contains(String name) {
        return get(name) != null;
    }

    /**
     * 返回全部请求头名称；同名请求头（写法不同）只保留首次出现的写法。
     *
     * @return 不可修改的名称集合，按首次出现顺序排列
     */
    public Set<String> names() {
        Set<String> canonical = new LinkedHashSet<>();
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < namesAndValues.size(); index += 2) {
            String name = namesAndValues.get(index);
            if (canonical.add(name.toLowerCase(Locale.ROOT))) {
                result.add(name);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回请求头条目数。
     *
     * @return 名称/值对的数量
     */
    public int size() {
        return namesAndValues.size() / 2;
    }

    /**
     * 判断是否不含任何请求头。
     *
     * @return 不含请求头时返回 true
     */
    public boolean isEmpty() {
        return namesAndValues.isEmpty();
    }

    /**
     * 返回指定下标的请求头名称。
     *
     * @param index 条目下标，范围为 0 至 {@link #size()} - 1
     * @return 对应的请求头名称
     */
    public String name(int index) {
        checkIndex(index);
        return namesAndValues.get(index * 2);
    }

    /**
     * 返回指定下标的请求头值。
     *
     * @param index 条目下标，范围为 0 至 {@link #size()} - 1
     * @return 对应的请求头值
     */
    public String value(int index) {
        checkIndex(index);
        return namesAndValues.get(index * 2 + 1);
    }

    /**
     * 返回适用于元数据和诊断的安全快照。
     *
     * @return 敏感值已脱敏的请求头副本
     */
    public HttpHeaders redacted() {
        if (isEmpty()) {
            return this;
        }
        Builder builder = builder();
        for (int index = 0; index < size(); index++) {
            String name = name(index);
            builder.add(name, HttpRedactionUtils.redactHeaderValue(name, value(index)));
        }
        return builder.build();
    }

    static HttpHeaders fromOkHttp(okhttp3.Headers headers) {
        Builder builder = builder();
        for (int index = 0; index < headers.size(); index++) {
            builder.add(headers.name(index), headers.value(index));
        }
        return builder.build();
    }

    okhttp3.Headers toOkHttp() {
        okhttp3.Headers.Builder builder = new okhttp3.Headers.Builder();
        for (int index = 0; index < size(); index++) {
            // 值在插入时已校验，因此使用常规的安全 OkHttp API 即可。
            builder.add(name(index), value(index));
        }
        return builder.build();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private static void requireName(@Nullable String name) {
        ValidationUtils.requireNonNull(name, "header name must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("header name must not be empty");
        }
        for (int index = 0; index < name.length(); index++) {
            char c = name.charAt(index);
            if (!isTokenCharacter(c)) {
                throw new IllegalArgumentException("invalid HTTP header name");
            }
        }
    }

    private static void requireValue(@Nullable String value) {
        ValidationUtils.requireNonNull(value, "header value must not be null");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c != '\t' && (c < 0x20 || c > 0x7e)) {
                throw new IllegalArgumentException("invalid HTTP header value");
            }
        }
    }

    private static boolean isTokenCharacter(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof HttpHeaders other && namesAndValues.equals(other.namesAndValues);
    }

    @Override
    public int hashCode() {
        return namesAndValues.hashCode();
    }

    /**
     * 敏感值始终会被脱敏。
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < size(); index++) {
            String name = name(index);
            result.append(name).append(": ")
                    .append(HttpRedactionUtils.redactHeaderValue(name, value(index)))
                    .append('\n');
        }
        return result.toString();
    }

    /**
     * {@link HttpHeaders} 的构建器；名称和值在插入时即校验。
     */
    public static final class Builder {
        private final List<String> namesAndValues = new ArrayList<>();

        private Builder() {
        }

        /**
         * 追加一个请求头；同名请求头可以多次出现。
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder add(String name, String value) {
            requireName(name);
            requireValue(value);
            namesAndValues.add(name);
            namesAndValues.add(value);
            return this;
        }

        /**
         * 设置请求头：先移除全部同名条目再追加，保证名称唯一。
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder set(String name, String value) {
            requireName(name);
            requireValue(value);
            remove(name);
            return add(name, value);
        }

        /**
         * 按大小写不敏感规则移除全部同名请求头。
         *
         * @param name 请求头名称
         * @return 当前构建器
         */
        public Builder remove(String name) {
            requireName(name);
            for (int index = namesAndValues.size() - 2; index >= 0; index -= 2) {
                if (namesAndValues.get(index).equalsIgnoreCase(name)) {
                    namesAndValues.remove(index + 1);
                    namesAndValues.remove(index);
                }
            }
            return this;
        }

        /**
         * 追加映射中的全部请求头。
         *
         * @param headers 请求头名称到值的映射
         * @return 当前构建器
         */
        public Builder addAll(Map<String, String> headers) {
            ValidationUtils.requireNonNull(headers, "headers must not be null");
            headers.forEach(this::add);
            return this;
        }

        /**
         * 追加另一个 {@link HttpHeaders} 中的全部请求头。
         *
         * @param headers 待追加的请求头
         * @return 当前构建器
         */
        public Builder addAll(HttpHeaders headers) {
            ValidationUtils.requireNonNull(headers, "headers must not be null");
            for (int index = 0; index < headers.size(); index++) {
                add(headers.name(index), headers.value(index));
            }
            return this;
        }

        /**
         * 构建不可变请求头实例。
         *
         * @return 新的请求头实例；不含任何条目时返回空实例
         */
        public HttpHeaders build() {
            return namesAndValues.isEmpty() ? EMPTY : new HttpHeaders(namesAndValues);
        }
    }
}
