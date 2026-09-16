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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * 仅依赖 JDK 的字节流工具，资源所有权与内存上限约定明确。
 */
public final class ByteStreamUtils {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final long DEFAULT_MAX_BYTES = 16L * 1024 * 1024;
    private static final long MAX_BYTE_ARRAY_SIZE = Integer.MAX_VALUE - 8L;

    private ByteStreamUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从借入的输入流复制到借入的输出流；两个流都不关闭，输出流也不刷新。
     *
     * @param input  待读取的输入流
     * @param output 待写入的输出流
     * @return 已复制的字节数
     * @throws IOException 读取或写入失败
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(output, "output");
        return input.transferTo(output);
    }

    /**
     * 在大小上限内把借入输入流的全部内容复制到借入输出流；两个流都不关闭，输出流也不刷新。
     *
     * <p>只有读到输入流结尾才能确认内容完整且未超限。复制满 {@code maximumBytes} 字节后，
     * 方法会再同步读一个字节，以区分“恰好达到上限”和“已经超限”；对尚未结束的长生命周期流，
     * 这次探测可能阻塞到新字节到达、流结束或底层读取超时。
     *
     * <p>复制不是事务性操作。内容超限时，输出流已写入 {@code maximumBytes} 字节，输入流也被
     * 额外消费了第 {@code maximumBytes + 1} 个字节，但该探测字节不会写入输出流。
     *
     * @param input 待读取的输入流
     * @param output 待写入的输出流
     * @param maximumBytes 允许复制的最大字节数
     * @return 已复制的字节数
     * @throws IllegalArgumentException 最大字节数为负数
     * @throws SizeLimitExceededException 内容超过上限；抛出前输出与输入已发生上述变化
     * @throws IOException 读取或写入失败
     */
    public static long copyWithLimit(InputStream input, OutputStream output, long maximumBytes)
            throws IOException {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(output, "output");
        validateNonNegativeMaximum(maximumBytes);

        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long total = 0;
        while (total < maximumBytes) {
            int length = (int) Math.min(buffer.length, maximumBytes - total);
            int read = input.read(buffer, 0, length);
            if (read < 0) {
                return total;
            }
            if (read == 0) {
                // read(buf, off, len) 契约规定仅 len == 0 时才可返回 0，而此处保证 len >= 1；
                // 返回 0 说明流实现已违约，继续循环只会永久自旋，因此立即显式失败
                throw new IOException("InputStream.read returned 0 while requested length > 0");
            }
            output.write(buffer, 0, read);
            total += read;
        }

        // 已达到上限时只探测一个字节，不把超限内容写入输出流。
        if (input.read() >= 0) {
            long observedBytes = maximumBytes == Long.MAX_VALUE
                    ? Long.MAX_VALUE : maximumBytes + 1;
            throw new SizeLimitExceededException(maximumBytes, observedBytes);
        }
        return total;
    }

    /**
     * 打开源流并在复制后关闭；借入的输出流不关闭、不刷新。
     *
     * @param source 提供输入流的源
     * @param output 待写入的输出流
     * @return 已复制的字节数
     * @throws IOException 打开、读取或写入失败
     */
    public static long copy(InputStreamSource source, OutputStream output) throws IOException {
        ValidationUtils.requireNonNull(source, "source");
        ValidationUtils.requireNonNull(output, "output");
        try (InputStream input = requireOpened(source)) {
            return copy(input, output);
        }
    }

    /**
     * 打开并关闭两个由本方法创建的流。
     *
     * @param source  提供输入流的源
     * @param target  写入目标路径
     * @param options 打开目标文件的选项
     * @return 已复制的字节数
     * @throws IOException 打开、读取或写入失败
     */
    public static long copy(InputStreamSource source, Path target, OpenOption... options)
            throws IOException {
        ValidationUtils.requireNonNull(source, "source");
        ValidationUtils.requireNonNull(target, "target");
        ValidationUtils.requireNonNull(options, "options");
        try (InputStream input = requireOpened(source);
             OutputStream output = Files.newOutputStream(target, options)) {
            return copy(input, output);
        }
    }

    /**
     * 以默认 16 MiB 上限读取借入的流，不关闭该流。
     *
     * @param input 待读取的输入流
     * @return 完整字节内容
     * @throws IOException 读取失败或内容超过上限
     */
    public static byte[] readAllBytes(InputStream input) throws IOException {
        return readAllBytes(input, DEFAULT_MAX_BYTES);
    }

    /**
     * 读取借入的流，最多 {@code maximumBytes} 字节，不关闭该流。
     *
     * @param input        待读取的输入流
     * @param maximumBytes 允许读取的最大字节数
     * @return 完整字节内容
     * @throws IOException 读取失败或内容超过上限
     */
    public static byte[] readAllBytes(InputStream input, long maximumBytes) throws IOException {
        ValidationUtils.requireNonNull(input, "input");
        validateMaximumBytes(maximumBytes);

        int initialCapacity = (int) Math.min(DEFAULT_BUFFER_SIZE, maximumBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
        copyWithLimit(input, output, maximumBytes);
        return output.toByteArray();
    }

    /**
     * 打开源流做有界读取并在结束后关闭。
     *
     * @param source       提供输入流的源
     * @param maximumBytes 允许读取的最大字节数
     * @return 完整字节内容
     * @throws IOException 打开、读取失败或内容超过上限
     */
    public static byte[] readAllBytes(InputStreamSource source, long maximumBytes)
            throws IOException {
        ValidationUtils.requireNonNull(source, "source");
        try (InputStream input = requireOpened(source)) {
            return readAllBytes(input, maximumBytes);
        }
    }

    /**
     * 按默认上限读取源流并在结束后关闭。
     *
     * @param source 提供输入流的源
     * @return 完整字节内容
     * @throws IOException 打开、读取失败或内容超过上限
     */
    public static byte[] readAllBytes(InputStreamSource source) throws IOException {
        return readAllBytes(source, DEFAULT_MAX_BYTES);
    }

    /**
     * 按指定字符集读取借入的输入流并解码为文本，不关闭该流。
     *
     * @param input        待读取的输入流
     * @param charset      解码字符集
     * @param maximumBytes 允许读取的最大字节数
     * @return 解码后的文本
     * @throws IOException 读取失败或内容超过上限
     */
    public static String readString(InputStream input, Charset charset, long maximumBytes)
            throws IOException {
        ValidationUtils.requireNonNull(charset, "charset");
        return new String(readAllBytes(input, maximumBytes), charset);
    }

    /**
     * 打开、读取并关闭源流，再按指定字符集解码为文本。
     *
     * @param source       提供输入流的源
     * @param charset      解码字符集
     * @param maximumBytes 允许读取的最大字节数
     * @return 解码后的文本
     * @throws IOException 打开、读取失败或内容超过上限
     */
    public static String readString(InputStreamSource source, Charset charset, long maximumBytes)
            throws IOException {
        ValidationUtils.requireNonNull(charset, "charset");
        return new String(readAllBytes(source, maximumBytes), charset);
    }

    /**
     * 按 UTF-8 读取借入的输入流并解码为文本，不关闭该流。
     *
     * @param input        待读取的输入流
     * @param maximumBytes 允许读取的最大字节数
     * @return 解码后的 UTF-8 文本
     * @throws IOException 读取失败或内容超过上限
     */
    public static String readUtf8(InputStream input, long maximumBytes) throws IOException {
        return readString(input, StandardCharsets.UTF_8, maximumBytes);
    }

    private static InputStream requireOpened(InputStreamSource source) throws IOException {
        return ValidationUtils.requireNonNull(source.openStream(), "source.openStream()");
    }

    private static void validateMaximumBytes(long maximumBytes) {
        if (maximumBytes < 0 || maximumBytes > MAX_BYTE_ARRAY_SIZE) {
            throw new IllegalArgumentException(
                    "maximumBytes must be between 0 and " + MAX_BYTE_ARRAY_SIZE + ": " + maximumBytes);
        }
    }

    private static void validateNonNegativeMaximum(long maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximumBytes must not be negative: " + maximumBytes);
        }
    }
}
