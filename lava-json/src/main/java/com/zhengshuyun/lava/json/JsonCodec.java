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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 不可变且线程安全的 JSON 编解码器。
 *
 * <p>原始 {@link InputStream} 仅被借入，绝不关闭；{@link Path} 由本编解码器打开并关闭。
 * 需要高级 Jackson 操作时，可通过 {@link #mapper()} 拿到底层 mapper。
 */
public final class JsonCodec {

    /**
     * 进程级共享的默认编解码器，随类加载初始化。
     */
    private static final JsonCodec DEFAULT = new JsonCodec(JsonMapperFactory.defaultMapper());

    /**
     * 底层的不可变 Jackson mapper。
     */
    private final ObjectMapper mapper;

    /**
     * 以指定 mapper 创建编解码器。
     *
     * @param mapper 不可为 null 的 Jackson mapper
     */
    public JsonCodec(ObjectMapper mapper) {
        this.mapper = ValidationUtils.requireNonNull(mapper, "mapper");
    }

    /**
     * 返回进程级共享的默认编解码器。
     */
    public static JsonCodec defaultCodec() {
        return DEFAULT;
    }

    /**
     * 返回底层不可变 Jackson mapper，供高级场景直接使用。
     */
    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * 将值编码为紧凑 JSON 字符串。
     *
     * @param value 待编码的值，可以为 null
     * @return JSON 文本
     * @throws JsonException 编码失败时抛出
     */
    public String write(@Nullable Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw encodingFailure(exception);
        }
    }

    /**
     * 将值编码为格式化的 JSON 字符串。
     *
     * @param value 待编码的值，可以为 null
     * @return 含缩进和换行的 JSON 文本
     * @throws JsonException 编码失败时抛出
     */
    public String writePretty(@Nullable Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception exception) {
            throw encodingFailure(exception);
        }
    }

    /**
     * 将值编码为 UTF-8 JSON 字节。
     *
     * @param value 待编码的值，可以为 null
     * @return JSON 字节数组
     * @throws JsonException 编码失败时抛出
     */
    public byte[] writeBytes(@Nullable Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw encodingFailure(exception);
        }
    }

    /**
     * 将 JSON 文本反序列化为目标类型。
     *
     * @param content 非空 JSON 文本
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(String content, Class<T> type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(content, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 将 JSON 文本反序列化为包含泛型信息的目标类型。
     *
     * @param content 非空 JSON 文本
     * @param type    保存泛型类型信息的类型引用
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(String content, TypeReference<T> type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(content, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 将 JSON 文本反序列化为 Jackson 类型模型指定的对象。
     *
     * @param content 非空 JSON 文本
     * @param type    Jackson 目标类型模型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public Object read(String content, JavaType type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(content, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 将 JSON 字节反序列化为目标类型。
     *
     * @param content 非空 JSON 字节
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(byte[] content, Class<T> type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(content, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 将 JSON 字节反序列化为包含泛型信息的目标类型。
     *
     * @param content 非空 JSON 字节
     * @param type    保存泛型类型信息的类型引用
     * @param <T>     目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(byte[] content, TypeReference<T> type) {
        ValidationUtils.requireNonNull(content, "content");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(content, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 从借入的流反序列化为目标类型，不关闭该流。
     *
     * @param input 待读取的流
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(InputStream input, Class<T> type) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(nonClosing(input), type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 从借入的流中反序列化包含泛型信息的目标类型，不会关闭调用方的流。
     *
     * @param input 待读取的流
     * @param type  保存泛型类型信息的类型引用
     * @param <T>   目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(InputStream input, TypeReference<T> type) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.readValue(nonClosing(input), type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 打开并关闭指定文件，反序列化为目标类型。
     *
     * @param path JSON 文件路径
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(Path path, Class<T> type) {
        ValidationUtils.requireNonNull(path, "path");
        ValidationUtils.requireNonNull(type, "type");
        try (InputStream input = Files.newInputStream(path)) {
            return requireDocumentValue(mapper.readValue(input, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 打开并关闭指定文件，然后反序列化为包含泛型信息的目标类型。
     *
     * @param path JSON 文件路径
     * @param type 保存泛型类型信息的类型引用
     * @param <T>  目标类型
     * @return 反序列化结果，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public <T> T read(Path path, TypeReference<T> type) {
        ValidationUtils.requireNonNull(path, "path");
        ValidationUtils.requireNonNull(type, "type");
        try (InputStream input = Files.newInputStream(path)) {
            return requireDocumentValue(mapper.readValue(input, type));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 将 JSON 文本解析为树模型。
     *
     * @param content 非空 JSON 文本
     * @return 根节点，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public JsonNode readTree(String content) {
        ValidationUtils.requireNonNull(content, "content");
        try {
            return requireDocumentValue(mapper.readTree(content));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 从借入的流读取树模型，不关闭该流。
     *
     * @param input 待读取的流
     * @return 根节点，永不为 null
     * @throws JsonException 文档为 JSON null、内容为空或解析失败时抛出
     */
    public JsonNode readTree(InputStream input) {
        ValidationUtils.requireNonNull(input, "input");
        try {
            return requireDocumentValue(mapper.readTree(nonClosing(input)));
        } catch (Exception exception) {
            throw decodingFailure(exception);
        }
    }

    /**
     * 在内存中将 JSON 兼容值转换为指定类型。
     *
     * @param value 待转换的值，为 null 时抛 {@link JsonException}（null 不携带类型信息，无转换语义）
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 转换结果，永不为 null
     * @throws JsonException 值为 null、值与目标类型不兼容或转换失败时抛出
     */
    public <T> T convert(@Nullable Object value, Class<T> type) {
        ValidationUtils.requireNonNull(type, "type");
        try {
            return requireDocumentValue(mapper.convertValue(value, type));
        } catch (Exception exception) {
            throw wrappingFailure(exception, "Failed to convert JSON-compatible value");
        }
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
     * 返回类型工厂，用于构造泛型 {@link JavaType}。
     *
     * @return 此 mapper 的类型工厂
     */
    public TypeFactory typeFactory() {
        return mapper.getTypeFactory();
    }

    private static InputStream nonClosing(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public void close() {
                // 借入的流归调用方所有。
            }
        };
    }

    private static <T> T requireDocumentValue(@Nullable T value) {
        if (value == null) {
            throw new JsonException("JSON document does not contain a value");
        }
        return value;
    }

    private static JsonException encodingFailure(Exception cause) {
        return wrappingFailure(cause, "Failed to encode JSON");
    }

    private static JsonException decodingFailure(Exception cause) {
        return wrappingFailure(cause, "Failed to decode JSON");
    }

    /**
     * 统一的异常出口：内部（如 requireDocumentValue）已抛出的 {@link JsonException} 携带精确语义，
     * 原样透传避免被泛化消息二次包装；外部异常才包装为指定消息。
     */
    private static JsonException wrappingFailure(Exception cause, String message) {
        if (cause instanceof JsonException jsonException) {
            return jsonException;
        }
        return new JsonException(message, cause);
    }
}
