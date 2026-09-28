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
import com.zhengshuyun.lava.json.JsonCodec;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP 请求体工具类；OkHttp 类型不会出现在这些方法的签名中。
 */
public final class HttpBodyUtils {
    private HttpBodyUtils() {
    }

    /**
     * 创建基于字节数组的可重放请求体。
     *
     * @param value       请求正文字节，会在构建时复制
     * @param contentType 请求体媒体类型
     * @return 传输无关请求体
     */
    public static HttpBody bytes(byte[] value, String contentType) {
        byte[] copy = ValidationUtils.requireNonNull(value, "value must not be null").clone();
        requireMediaType(contentType);
        return new HttpBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                output.write(copy);
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public long contentLength() {
                return copy.length;
            }
        };
    }

    /**
     * 使用 UTF-8 和 text/plain 创建文本请求体。
     *
     * @param value 文本正文
     * @return 文本请求体
     */
    public static HttpBody text(String value) {
        return text(value, StandardCharsets.UTF_8, HttpMediaTypes.TEXT_PLAIN);
    }

    /**
     * 使用指定字符集和媒体类型创建文本请求体。
     *
     * @param value       文本正文
     * @param charset     文本编码
     * @param contentType 媒体类型
     * @return 文本请求体
     */
    public static HttpBody text(String value, Charset charset, String contentType) {
        ValidationUtils.requireNonNull(value, "value must not be null");
        ValidationUtils.requireNonNull(charset, "charset must not be null");
        return bytes(value.getBytes(charset), withCharset(contentType, charset));
    }

    /**
     * 使用默认 JSON 编解码器创建 JSON 请求体。
     *
     * @param value 待序列化对象
     * @return JSON 请求体
     */
    public static HttpBody json(Object value) {
        return json(value, JsonCodec.defaultCodec());
    }

    /**
     * 使用指定 JSON 编解码器创建 JSON 请求体。
     *
     * @param value 待序列化对象
     * @param codec JSON 编解码器
     * @return JSON 请求体
     */
    public static HttpBody json(Object value, JsonCodec codec) {
        return bytes(ValidationUtils.requireNonNull(codec, "codec must not be null").writeBytes(value),
                HttpMediaTypes.APPLICATION_JSON);
    }

    /**
     * 创建 application/x-www-form-urlencoded 请求体。
     *
     * @param values 表单字段名称和值
     * @return 表单请求体
     */
    public static HttpBody form(Map<String, String> values) {
        ValidationUtils.requireNonNull(values, "values must not be null");
        okhttp3.FormBody.Builder builder = new okhttp3.FormBody.Builder(StandardCharsets.UTF_8);
        values.forEach(builder::add);
        return fromOkHttp(builder.build());
    }

    /**
     * 创建从常规文件读取的请求体。
     *
     * @param path        待读取的常规文件路径
     * @param contentType 文件媒体类型
     * @return 文件请求体
     */
    public static HttpBody file(Path path, String contentType) {
        ValidationUtils.requireNonNull(path, "path must not be null");
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("path must be a regular file");
        }
        requireMediaType(contentType);
        return fromOkHttp(RequestBody.create(path.toFile(), MediaType.parse(contentType)));
    }

    /**
     * 创建从输入流读取的一次性请求体，调用方负责关闭输入流。
     *
     * <p>传输层不会为自动重试或保留正文的重定向再次消费该输入流。</p>
     *
     * @param input       正文输入流
     * @param length      正文字节数；未知时为 -1
     * @param contentType 请求体媒体类型
     * @return 流式请求体
     */
    public static HttpBody stream(InputStream input, long length, String contentType) {
        ValidationUtils.requireNonNull(input, "input must not be null");
        if (length < -1L) {
            throw new IllegalArgumentException("length must be >= -1");
        }
        requireMediaType(contentType);
        return new HttpBody() {
            @Override
            public void writeTo(OutputStream output) throws IOException {
                input.transferTo(output);
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public long contentLength() {
                return length;
            }

            /** 返回一次性标记，禁止传输层重放已消费的输入流。 */
            @Override
            public boolean isOneShot() {
                return true;
            }
        };
    }

    /**
     * 将高级 multipart 构建器转换为传输无关请求体。
     *
     * @param multipart 已构建完成的 multipart 表单
     * @return multipart 请求体
     */
    public static HttpBody multipart(HttpRequest.MultipartBuilder multipart) {
        return fromOkHttp(ValidationUtils.requireNonNull(multipart, "multipart must not be null").build());
    }

    static HttpBody fromOkHttp(RequestBody body) {
        return new OkHttpBackedBody(ValidationUtils.requireNonNull(body, "body must not be null"));
    }

    /**
     * 将传输无关请求体适配为 OkHttp 请求体，同时保留正文长度和可重放性。
     *
     * @param body 待适配的请求体
     * @return 供 OkHttp 发送的请求体
     */
    static RequestBody toOkHttp(HttpBody body) {
        if (body instanceof OkHttpBackedBody backed) {
            return backed.body;
        }
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                String type = body.contentType();
                return type == null ? null : MediaType.parse(type);
            }

            @Override
            public long contentLength() {
                return body.contentLength();
            }

            /** 将一次性约束交给 OkHttp，阻止重试或重定向隐式重复上传。 */
            @Override
            public boolean isOneShot() {
                return body.isOneShot();
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                body.writeTo(sink.outputStream());
            }
        };
    }

    private static String withCharset(String contentType, Charset charset) {
        requireMediaType(contentType);
        return contentType.toLowerCase(Locale.ROOT).contains("charset=")
                ? contentType : contentType + "; charset=" + charset.name().toLowerCase(Locale.ROOT);
    }

    private static void requireMediaType(String contentType) {
        ValidationUtils.requireNonNull(contentType, "contentType must not be null");
        if (MediaType.parse(contentType) == null) {
            throw new IllegalArgumentException("invalid content type");
        }
    }

    private static final class OkHttpBackedBody implements HttpBody {
        private final RequestBody body;

        private OkHttpBackedBody(RequestBody body) {
            this.body = body;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            okio.BufferedSink sink = okio.Okio.buffer(okio.Okio.sink(output));
            body.writeTo(sink);
            sink.flush();
        }

        @Override
        public String contentType() {
            MediaType type = body.contentType();
            return type == null ? null : type.toString();
        }

        @Override
        public long contentLength() {
            try {
                return body.contentLength();
            } catch (IOException ignored) {
                return -1L;
            }
        }

        /** 保留原生请求体的一次性约束，供进一步包装或组合时使用。 */
        @Override
        public boolean isOneShot() {
            return body.isOneShot();
        }
    }
}
