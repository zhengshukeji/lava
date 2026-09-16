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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ByteStreamUtilsTest {

    @Test
    void borrowedStreamsAreNeverClosedOrFlushed() throws Exception {
        TrackingInput input = new TrackingInput("lava".getBytes(StandardCharsets.UTF_8));
        TrackingOutput output = new TrackingOutput();

        assertEquals(4, ByteStreamUtils.copy(input, output));
        assertEquals("lava", output.toString(StandardCharsets.UTF_8));
        assertFalse(input.closed);
        assertFalse(output.closed);
        assertFalse(output.flushed);

        assertArrayEquals(new byte[0], ByteStreamUtils.readAllBytes(input, 4));
        assertFalse(input.closed);
    }

    @Test
    void copyWithLimitCopiesCompleteInputAndReportsOverflow() throws Exception {
        TrackingInput exact = new TrackingInput(new byte[]{1, 2, 3});
        TrackingOutput exactOutput = new TrackingOutput();
        assertEquals(3, ByteStreamUtils.copyWithLimit(exact, exactOutput, 3));
        assertArrayEquals(new byte[]{1, 2, 3}, exactOutput.toByteArray());

        TrackingInput exceeded = new TrackingInput(new byte[]{1, 2, 3, 4});
        TrackingOutput limitedOutput = new TrackingOutput();
        SizeLimitExceededException failure = assertThrows(
                SizeLimitExceededException.class,
                () -> ByteStreamUtils.copyWithLimit(exceeded, limitedOutput, 3));
        assertEquals(3, failure.maximumBytes());
        assertEquals(4, failure.observedBytes());
        assertArrayEquals(new byte[]{1, 2, 3}, limitedOutput.toByteArray());
        assertFalse(exceeded.closed);
        assertFalse(limitedOutput.closed);
        assertFalse(limitedOutput.flushed);
        assertEquals(-1, exceeded.read(), "超限探测字节应已从输入流消费");
    }

    @Test
    void copyWithLimitSupportsZeroAndRejectsNegativeLimits() throws Exception {
        assertEquals(0, ByteStreamUtils.copyWithLimit(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 0));
        assertThrows(SizeLimitExceededException.class, () -> ByteStreamUtils.copyWithLimit(
                new ByteArrayInputStream(new byte[]{1}), new ByteArrayOutputStream(), 0));
        assertThrows(IllegalArgumentException.class, () -> ByteStreamUtils.copyWithLimit(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), -1));
    }

    @Test
    void copyWithLimitFailsFastWhenStreamViolatesReadContract() {
        // read(buf, off, len) 契约: len >= 1 时不应返回 0; 始终返回 0 的流若被无限容忍会永久自旋
        InputStream stalled = new InputStream() {
            @Override
            public int read(byte[] buffer, int offset, int length) {
                return 0;
            }

            @Override
            public int read() {
                return 0;
            }
        };
        assertThrows(IOException.class,
                () -> ByteStreamUtils.copyWithLimit(stalled, new ByteArrayOutputStream(), 16));
    }

    @Test
    void sourceStreamsAreOwnedAndClosed() throws Exception {
        AtomicReference<TrackingInput> opened = new AtomicReference<>();
        InputStreamSource source = () -> {
            TrackingInput input = new TrackingInput(new byte[]{1, 2, 3});
            opened.set(input);
            return input;
        };
        TrackingOutput output = new TrackingOutput();

        assertEquals(3, ByteStreamUtils.copy(source, output));
        assertTrue(opened.get().closed);
        assertFalse(output.closed);
        assertFalse(output.flushed);
    }

    @Test
    void ownedSourceClosesWhenCopyFails() {
        AtomicReference<TrackingInput> opened = new AtomicReference<>();
        InputStreamSource source = () -> {
            TrackingInput input = new TrackingInput(new byte[]{1});
            opened.set(input);
            return input;
        };
        OutputStream failingOutput = new OutputStream() {

            @Override
            public void write(int value) throws IOException {
                throw new IOException("测试写入失败");
            }
        };

        assertThrows(IOException.class, () -> ByteStreamUtils.copy(source, failingOutput));
        assertTrue(opened.get().closed);
    }

    @Test
    void boundedReadFailsAsSoonAsLimitIsCrossed() {
        TrackingInput input = new TrackingInput(new byte[]{1, 2, 3, 4});
        SizeLimitExceededException failure = assertThrows(
                SizeLimitExceededException.class,
                () -> ByteStreamUtils.readAllBytes(input, 3));

        assertEquals(3, failure.maximumBytes());
        assertTrue(failure.observedBytes() > failure.maximumBytes());
        assertFalse(input.closed);
        assertThrows(IllegalArgumentException.class, () -> ByteStreamUtils.readAllBytes(input, -1));
        assertThrows(IllegalArgumentException.class, () -> ByteStreamUtils.readAllBytes(
                new ByteArrayInputStream(new byte[0]), Integer.MAX_VALUE));
    }

    @Test
    void stringReadersHonorCharsetByteLimitAndStreamOwnership() throws Exception {
        Charset charset = StandardCharsets.UTF_16LE;
        byte[] encoded = "整数".getBytes(charset);
        TrackingInput input = new TrackingInput(encoded);

        assertEquals("整数", ByteStreamUtils.readString(input, charset, encoded.length));
        assertFalse(input.closed);
        assertEquals("Lava", ByteStreamUtils.readUtf8(
                new ByteArrayInputStream("Lava".getBytes(StandardCharsets.UTF_8)), 4));
        assertEquals("整数", ByteStreamUtils.readString(
                InputStreamSource.fromBytes(encoded), charset, encoded.length));
    }

    @Test
    void sourceFactoriesAreRepeatableAndSnapshotByteArrays(@TempDir Path directory)
            throws Exception {
        byte[] mutable = "one".getBytes(StandardCharsets.UTF_8);
        InputStreamSource bytes = InputStreamSource.fromBytes(mutable);
        mutable[0] = 'X';
        assertEquals("one", new String(ByteStreamUtils.readAllBytes(bytes, 3), StandardCharsets.UTF_8));
        assertEquals("one", new String(ByteStreamUtils.readAllBytes(bytes, 3), StandardCharsets.UTF_8));

        Path target = directory.resolve("copy.bin");
        assertEquals(3, ByteStreamUtils.copy(bytes, target));
        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;
        private boolean flushed;

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
