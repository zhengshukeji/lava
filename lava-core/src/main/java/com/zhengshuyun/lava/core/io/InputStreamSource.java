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

package com.zhengshuyun.lava.core.io;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * 可重复打开的输入流来源。
 *
 * <p>每次 {@link #openStream()} 成功调用都必须返回一个新流。接收本抽象的 Lava API 会拥有并
 * 关闭打开的流；相对地，接收原始 {@link InputStream} 的 API 只借用该流，绝不关闭。
 */
@FunctionalInterface
public interface InputStreamSource {

    /**
     * 打开一个新流，由调用方负责关闭。
     *
     * @return 新打开的输入流
     * @throws IOException 打开失败
     */
    InputStream openStream() throws IOException;

    /**
     * 创建每次都从指定路径新开流的来源。
     *
     * @param path    待读取的文件路径
     * @param options 打开文件的选项
     * @return 可重复打开的输入流来源
     */
    static InputStreamSource fromPath(Path path, OpenOption... options) {
        ValidationUtils.requireNonNull(path, "path");
        OpenOption[] copiedOptions = ValidationUtils.requireNonNull(options, "options").clone();
        for (OpenOption option : copiedOptions) {
            ValidationUtils.requireNonNull(option, "option");
        }
        return () -> Files.newInputStream(path, copiedOptions);
    }

    /**
     * 创建以字节数组快照为内容的来源，后续修改原数组不影响已创建的来源。
     *
     * @param bytes 待复制的字节内容
     * @return 可重复打开的输入流来源
     */
    static InputStreamSource fromBytes(byte[] bytes) {
        byte[] snapshot = ValidationUtils.requireNonNull(bytes, "bytes").clone();
        return () -> new ByteArrayInputStream(snapshot);
    }

    /**
     * 以 UTF-8 编码文本创建来源。
     *
     * @param value 待编码的文本
     * @return 可重复打开的输入流来源
     */
    static InputStreamSource fromString(String value) {
        return fromString(value, StandardCharsets.UTF_8);
    }

    /**
     * 以指定字符集编码文本创建来源。
     *
     * @param value   待编码的文本
     * @param charset 文本编码字符集
     * @return 可重复打开的输入流来源
     */
    static InputStreamSource fromString(String value, Charset charset) {
        ValidationUtils.requireNonNull(value, "value");
        ValidationUtils.requireNonNull(charset, "charset");
        return fromBytes(value.getBytes(charset));
    }
}
