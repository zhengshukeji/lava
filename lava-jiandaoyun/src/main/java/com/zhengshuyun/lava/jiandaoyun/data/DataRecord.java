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
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunJsonUtils;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 简道云表单中的一条数据。
 *
 * <p>表单字段是动态的，业务字段通过 {@link #value(String)} 按字段名读取（字段名即字段别名，
 * 未设置别名时为 {@code _widget_xxx}）；固定的系统字段提供类型化访问器。也可以用
 * {@link #as(Class)} 转换为调用方自定义的 POJO。</p>
 */
public final class DataRecord {
    /** 原始数据，保持简道云返回的字段顺序。 */
    private final Map<String, @Nullable Object> values;

    /**
     * 使用接口返回的原始字段创建数据记录。
     *
     * @param values 原始字段
     */
    private DataRecord(Map<String, ?> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 由原始字段创建数据记录，例如 Webhook 推送正文中的 {@code data}。
     *
     * @param values 原始字段
     * @return 数据记录
     */
    public static DataRecord of(Map<String, ?> values) {
        return new DataRecord(ValidationUtils.requireNonNull(values, "values must not be null"));
    }

    /**
     * 返回数据 ID。
     *
     * @return 数据 ID（{@code _id}）
     */
    public @Nullable String id() {
        return text("_id");
    }

    /**
     * 返回所属应用 ID。
     *
     * @return 应用 ID
     */
    public @Nullable String appId() {
        return text("appId");
    }

    /**
     * 返回所属表单 ID。
     *
     * @return 表单 ID
     */
    public @Nullable String entryId() {
        return text("entryId");
    }

    /**
     * 返回数据提交人。
     *
     * @return 提交人；未返回时为 {@code null}
     */
    public @Nullable Member creator() {
        return member("creator");
    }

    /**
     * 返回数据最后修改人。
     *
     * @return 修改人；未返回时为 {@code null}
     */
    public @Nullable Member updater() {
        return member("updater");
    }

    /**
     * 返回数据删除人，仅删除事件推送中存在。
     *
     * @return 删除人；未返回时为 {@code null}
     */
    public @Nullable Member deleter() {
        return member("deleter");
    }

    /**
     * 返回数据提交时间。
     *
     * @return 提交时间；未返回时为 {@code null}
     */
    public @Nullable Instant createTime() {
        return instant("createTime");
    }

    /**
     * 返回数据最后修改时间。
     *
     * @return 修改时间；未返回时为 {@code null}
     */
    public @Nullable Instant updateTime() {
        return instant("updateTime");
    }

    /**
     * 返回数据删除时间，仅删除事件推送中存在。
     *
     * @return 删除时间；未返回时为 {@code null}
     */
    public @Nullable Instant deleteTime() {
        return instant("deleteTime");
    }

    /**
     * 按字段名读取字段值。
     *
     * <p>值类型与简道云 JSON 一致：文本为 {@code String}，数字为 {@code Number}，多选与子表单为
     * {@code List}，地址、成员等为 {@code Map}。</p>
     *
     * @param field 字段名
     * @return 字段值；不存在或为 JSON null 时为 {@code null}
     */
    public @Nullable Object value(String field) {
        return values.get(field);
    }

    /**
     * 返回全部原始字段，包括系统字段和业务字段。
     *
     * @return 不可变的字段映射
     */
    public Map<String, @Nullable Object> values() {
        return values;
    }

    /**
     * 将整条数据转换为调用方的 POJO，字段名映射通常需要在 POJO 上使用 {@code @JsonProperty("_widget_xxx")}。
     *
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 转换结果
     * @throws com.zhengshuyun.lava.json.JsonException 数据与目标类型不兼容时抛出
     */
    public <T> T as(Class<T> type) {
        return JiandaoyunJsonUtils.codec().convert(values, type);
    }

    /**
     * 读取文本字段。
     *
     * @param key 字段名
     * @return 文本；不是字符串时为 {@code null}
     */
    private @Nullable String text(String key) {
        return values.get(key) instanceof String text ? text : null;
    }

    /**
     * 读取 ISO-8601 时间字段。
     *
     * @param key 字段名
     * @return 时间；缺失时为 {@code null}
     */
    private @Nullable Instant instant(String key) {
        String text = text(key);
        return text == null ? null : Instant.parse(text);
    }

    /**
     * 读取成员对象字段。
     *
     * @param key 字段名
     * @return 成员；缺失时为 {@code null}
     */
    private @Nullable Member member(String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map ? JiandaoyunJsonUtils.codec().convert(map, Member.class) : null;
    }

    /**
     * 按原始字段比较两条数据。
     *
     * @param other 另一个对象
     * @return 原始字段相同时为 {@code true}
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof DataRecord record && values.equals(record.values);
    }

    /**
     * 返回基于原始字段的哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    /**
     * 返回仅包含数据 ID 的描述，避免日志打印业务字段。
     *
     * @return 诊断文本
     */
    @Override
    public String toString() {
        return "DataRecord[id=" + id() + ']';
    }
}
