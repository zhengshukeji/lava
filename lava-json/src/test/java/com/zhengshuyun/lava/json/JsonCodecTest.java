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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {

    record User(long id, String name) {
    }

    record TimePayload(Instant instant, LocalDate date, LocalDateTime localDateTime) {
    }

    record FieldFormatted(
            @JsonFormat(pattern = "uuuu/MM/dd HH:mm:ss") LocalDateTime value) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = Circle.class, name = "circle"))
    sealed interface Shape permits Circle {
    }

    record Circle(int radius) implements Shape {
    }

    record Drawing(Shape shape) {
    }

    @Test
    void defaultCodecIsDeterministicThreadSafeAndKeepsLongsNumeric() throws Exception {
        JsonCodec codec = JsonCodec.defaultCodec();
        assertSame(codec, JsonCodec.defaultCodec());
        assertSame(codec.mapper(), codec.mapper());

        String json = codec.write(new User(Long.MAX_VALUE, "lava"));
        assertTrue(codec.readTree(json).get("id").isIntegralNumber());
        assertEquals(new User(Long.MAX_VALUE, "lava"), codec.read(json, User.class));

        Set<String> documents = ConcurrentHashMap.newKeySet();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2_000; i++) {
                int id = i;
                futures.add(executor.submit(() -> {
                    User user = new User(id, "user-" + id);
                    String encoded = codec.write(user);
                    assertEquals(user, codec.read(encoded, User.class));
                    documents.add(encoded);
                }));
            }
            for (var future : futures) {
                future.get();
            }
        }
        assertEquals(2_000, documents.size());
    }

    @Test
    void supportsJavaTimeRecordsSealedTypesAndGenerics() {
        JsonCodec codec = JsonCodec.defaultCodec();
        TimePayload expected = new TimePayload(
                Instant.parse("2026-03-29T01:30:00.123456789Z"),
                LocalDate.of(2028, 2, 29),
                LocalDateTime.of(2026, 10, 25, 2, 30, 0, 987_654_321));
        assertEquals(expected, codec.read(codec.write(expected), TimePayload.class));

        Drawing drawing = new Drawing(new Circle(4));
        assertEquals(drawing, codec.read(codec.write(drawing), Drawing.class));

        List<User> users = List.of(new User(1, "a"), new User(2, "b"));
        List<User> decoded = codec.read(codec.write(users), new TypeReference<List<User>>() {
        });
        assertEquals(users, decoded);
    }

    @Test
    void factoryPatternsRemainBelowFieldAnnotationsAndLocaleIsExplicit() {
        JsonCodec codec = new JsonCodec(JsonMapperFactory.builder()
                .localDateTimePattern("dd MMM uuuu HH:mm:ss")
                .locale(Locale.US)
                .build());

        assertEquals(
                "{\"value\":\"2026/01/02 03:04:05\"}",
                codec.write(new FieldFormatted(LocalDateTime.of(2026, 1, 2, 3, 4, 5))));

        JsonCodec dateCodec = new JsonCodec(JsonMapperFactory.builder()
                .localDatePattern("dd MMM uuuu")
                .locale(Locale.US)
                .build());
        assertEquals("\"02 Jan 2026\"", dateCodec.write(LocalDate.of(2026, 1, 2)));
    }

    @Test
    void borrowedInputIsNotClosedWhilePathIsManaged(@TempDir Path directory) throws Exception {
        JsonCodec codec = JsonCodec.defaultCodec();
        TrackingInput input = new TrackingInput("{\"id\":1,\"name\":\"a\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals(new User(1, "a"), codec.read(input, User.class));
        assertFalse(input.closed);

        Path path = directory.resolve("user.json");
        Files.writeString(path, "[{\"id\":2,\"name\":\"b\"}]");
        List<User> users = codec.read(path, new TypeReference<List<User>>() {
        });
        assertEquals(List.of(new User(2, "b")), users);

        // 文件读取失败时 cause 直接是原始 IOException，不夹一层 UncheckedIOException
        JsonException missing = assertThrows(JsonException.class,
                () -> codec.read(directory.resolve("missing.json"), User.class));
        assertInstanceOf(java.nio.file.NoSuchFileException.class, missing.getCause());
    }

    @Test
    void failuresUseStableMessagesAndNullDocumentIsRejected() {
        JsonCodec codec = JsonCodec.defaultCodec();
        JsonException malformed = assertThrows(JsonException.class,
                () -> codec.read("{secret-token", User.class));
        assertEquals("Failed to decode JSON", malformed.getMessage());
        assertFalse(malformed.getMessage().contains("secret-token"));
        assertThrows(JsonException.class, () -> codec.read("null", User.class));
    }

    @Test
    void coversBinaryTreeConversionAndTypeEscapeHatches(@TempDir Path directory) throws Exception {
        JsonCodec codec = JsonCodec.defaultCodec();
        User user = new User(9, "nine");

        assertTrue(codec.writePretty(user).contains(System.lineSeparator()));
        byte[] bytes = codec.writeBytes(user);
        assertEquals(user, codec.read(bytes, User.class));
        assertEquals(
                List.of(user),
                codec.read(codec.writeBytes(List.of(user)), new TypeReference<List<User>>() {
                }));

        var listType = codec.typeFactory().constructCollectionType(List.class, User.class);
        assertEquals(List.of(user), codec.read(codec.write(List.of(user)), listType));

        TrackingInput typedInput = new TrackingInput(codec.writeBytes(List.of(user)));
        assertEquals(List.of(user), codec.read(typedInput, new TypeReference<List<User>>() {
        }));
        assertFalse(typedInput.closed);

        TrackingInput treeInput = new TrackingInput(bytes);
        assertEquals(9, codec.readTree(treeInput).get("id").intValue());
        assertFalse(treeInput.closed);

        Path path = directory.resolve("single.json");
        Files.write(path, bytes);
        assertEquals(user, codec.read(path, User.class));
        assertEquals(user, codec.convert(java.util.Map.of("id", 9, "name", "nine"), User.class));
        assertEquals(1, codec.objectNode().put("a", 1).size());
        assertEquals(1, codec.arrayNode().add(1).size());
    }

    @Test
    void factorySupportsAllExplicitSettingsAndRejectsBlankPatterns() {
        JsonCodec codec = new JsonCodec(JsonMapperFactory.builder()
                .localTimePattern("HH_mm_ss")
                .zone(ZoneOffset.UTC)
                .customize(builder -> {
                })
                .build());
        assertEquals("\"03_04_05\"", codec.write(LocalTime.of(3, 4, 5)));
        assertThrows(IllegalArgumentException.class,
                () -> JsonMapperFactory.builder().localDatePattern("  "));
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
}
