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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.type.TypeFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 不可变且线程安全的 JSON 编解码器。
 *
 * <p>所有失败统一抛出 {@link JsonException}，原始 Jackson 异常作为 cause 保留。读取方法永不返回
 * null：文档为 JSON {@code null} 或内容为空时同样抛出 {@link JsonException}。原始
 * {@link InputStream} 仅被借入，绝不关闭；{@link Path} 由本编解码器打开并关闭。需要更多 Jackson
 * 能力时，通过 {@link #mapper()} 直接使用底层 mapper。
 */
public final class JsonCodec {

    /**
     * 进程级共享的默认编解码器。
     */
    private static final JsonCodec DEFAULT = new JsonCodec(JsonMapperFactory.defaultMapper());

    private final ObjectMapper mapper;

    /**
     * 以指定 mapper 创建编解码器。
     *
     * @param mapper Jackson mapper
     */
    public JsonCodec(ObjectMapper mapper) {
        this.mapper = ValidationUtils.requireNonNull(mapper, "mapper");
    }

    /**
     * 返回进程级共享的默认编解码器。
     *
     * @return 默认编解码器
     */
    public static JsonCodec defaultCodec() {
        return DEFAULT;
    }

    /**
     * 返回底层不可变 Jackson mapper，供高级场景直接使用。
     *
     * @return 底层 mapper
     */
    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * 将值编码为紧凑 JSON 字符串。
     *
     * @param value 待编码的值，可以为 null
     * @return JSON 文本
     * @throws JsonException 编码失败
     */
    public String write(@Nullable Object value) {
        return encode(() -> mapper.writeValueAsString(value));
    }

    /**
     * 将值编码为带缩进的 JSON 字符串。
     *
     * @param value 待编码的值，可以为 null
     * @return 格式化的 JSON 文本
     * @throws JsonException 编码失败
     */
    public String writePretty(@Nullable Object value) {
        return encode(() -> mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    /**
     * 将值编码为 UTF-8 JSON 字节。
     *
     * @param value 待编码的值，可以为 null
     * @return JSON 字节数组
     * @throws JsonException 编码失败
     */
    public byte[] writeBytes(@Nullable Object value) {
        return encode(() -> mapper.writeValueAsBytes(value));
    }

    /**
     * 将 JSON 文本反序列化为目标类型。
     *
     * @param content JSON 文本
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(String content, Class<T> type) {
        requireArguments(content, type);
        return decode(() -> mapper.readValue(content, type));
    }

    /**
     * 将 JSON 文本反序列化为带泛型信息的目标类型。
     *
     * @param content JSON 文本
     * @param type    保存泛型信息的类型引用
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(String content, TypeReference<T> type) {
        requireArguments(content, type);
        return decode(() -> mapper.readValue(content, type));
    }

    /**
     * 将 JSON 文本反序列化为 Jackson 类型模型指定的对象。
     *
     * @param content JSON 文本
     * @param type    Jackson 目标类型模型，可由 {@link #typeFactory()} 构造
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public Object read(String content, JavaType type) {
        requireArguments(content, type);
        return decode(() -> mapper.readValue(content, type));
    }

    /**
     * 将 JSON 字节反序列化为目标类型。
     *
     * @param content JSON 字节
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(byte[] content, Class<T> type) {
        requireArguments(content, type);
        return decode(() -> mapper.readValue(content, type));
    }

    /**
     * 将 JSON 字节反序列化为带泛型信息的目标类型。
     *
     * @param content JSON 字节
     * @param type    保存泛型信息的类型引用
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(byte[] content, TypeReference<T> type) {
        requireArguments(content, type);
        return decode(() -> mapper.readValue(content, type));
    }

    /**
     * 从借入的流反序列化为目标类型，不关闭该流。
     *
     * @param input 待读取的流
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(InputStream input, Class<T> type) {
        requireArguments(input, type);
        return decode(() -> mapper.readValue(nonClosing(input), type));
    }

    /**
     * 从借入的流反序列化为带泛型信息的目标类型，不关闭该流。
     *
     * @param input 待读取的流
     * @param type  保存泛型信息的类型引用
     * @param <T>   目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(InputStream input, TypeReference<T> type) {
        requireArguments(input, type);
        return decode(() -> mapper.readValue(nonClosing(input), type));
    }

    /**
     * 打开并关闭指定文件，反序列化为目标类型。
     *
     * @param path JSON 文件路径
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文件读取失败、文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(Path path, Class<T> type) {
        requireArguments(path, type);
        return decode(() -> {
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, type);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * 打开并关闭指定文件，反序列化为带泛型信息的目标类型。
     *
     * @param path JSON 文件路径
     * @param type 保存泛型信息的类型引用
     * @param <T>  目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文件读取失败、文档为 JSON null、内容为空或解析失败
     */
    public <T> T read(Path path, TypeReference<T> type) {
        requireArguments(path, type);
        return decode(() -> {
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, type);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * 将 JSON 文本解析为树模型。
     *
     * @param content JSON 文本
     * @return 根节点，永不为 null
     * @throws JsonException 内容为空或解析失败
     */
    public JsonNode readTree(String content) {
        ValidationUtils.requireNonNull(content, "content");
        return decode(() -> mapper.readTree(content));
    }

    /**
     * 从借入的流读取树模型，不关闭该流。
     *
     * @param input 待读取的流
     * @return 根节点，永不为 null
     * @throws JsonException 内容为空或解析失败
     */
    public JsonNode readTree(InputStream input) {
        ValidationUtils.requireNonNull(input, "input");
        return decode(() -> mapper.readTree(nonClosing(input)));
    }

    /**
     * 在内存中将 JSON 兼容值转换为指定类型，例如 {@code Map} 转 POJO。
     *
     * @param value 待转换的值；为 null 时抛出 {@link JsonException}（null 不携带类型信息）
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 转换结果，永不为 null
     * @throws JsonException 值为 null、类型不兼容或转换失败
     */
    public <T> T convert(@Nullable Object value, Class<T> type) {
        ValidationUtils.requireNonNull(type, "type");
        return call(() -> mapper.convertValue(value, type), "Failed to convert JSON-compatible value");
    }

    /**
     * 创建空对象节点。
     *
     * @return 新的对象节点
     */
    public ObjectNode objectNode() {
        return mapper.createObjectNode();
    }

    /**
     * 创建空数组节点。
     *
     * @return 新的数组节点
     */
    public ArrayNode arrayNode() {
        return mapper.createArrayNode();
    }

    /**
     * 返回底层 mapper 的类型工厂，用于构造 {@link JavaType}。
     *
     * @return 类型工厂
     */
    public TypeFactory typeFactory() {
        return mapper.getTypeFactory();
    }

    private static void requireArguments(Object content, Object type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
    }

    private static <T> T encode(Supplier<@Nullable T> action) {
        return call(action, "Failed to encode JSON");
    }

    private static <T> T decode(Supplier<@Nullable T> action) {
        return call(action, "Failed to decode JSON");
    }

    /**
     * 统一执行 Jackson 调用：非 null 结果原样返回，null 结果与任何异常都转为 {@link JsonException}。
     */
    private static <T> T call(Supplier<@Nullable T> action, String failureMessage) {
        T value;
        try {
            value = action.get();
        } catch (JsonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonException(failureMessage, exception);
        }
        if (value == null) {
            throw new JsonException("JSON document does not contain a value");
        }
        return value;
    }

    private static InputStream nonClosing(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public void close() {
                // 借入的流归调用方所有，Jackson 读取结束时不得关闭
            }
        };
    }
}
