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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpModernApiTest {
    private LocalEchoServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        server = LocalEchoServer.start();
        client = HttpClient.builder()
                .baseUrl(server.baseUrl() + "/")
                .defaultHeader("X-Test", "default")
                .sseIdleTimeout(Duration.ofSeconds(3))
                .build();
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void relativeRequestUsesBaseUrlAndRequestHeaderWins() {
        HttpResponse response = client.send(HttpRequest.post("echo")
                .addQueryParam("q", "中文 value")
                .header("X-Test", "request")
                .jsonBody(Map.of("answer", 42))
                .build());

        assertTrue(response.isSuccessful());
        assertTrue(response.bodyString().contains("q=%E4%B8%AD%E6%96%87%20value"));
        assertTrue(response.bodyString().contains("|request|"));
        assertTrue(response.bodyString().contains("answer"));
    }

    @Test
    void typedJsonAndExplicitStatusCheckAreAvailable() {
        Message message = client.send(HttpRequest.get("json").build())
                .requireSuccess()
                .bodyAs(Message.class);
        assertEquals("ok", message.message());

        HttpStatusException failure = assertThrows(HttpStatusException.class,
                () -> client.send(HttpRequest.get("error").build()).requireSuccess());
        assertEquals(503, failure.statusCode());
        assertEquals("unavailable", failure.responseBody());
        assertFalse(failure.toString().contains("unavailable"));
    }

    @Test
    void portableBodyFactoriesWorkWithoutNativeTypes() {
        HttpResponse response = client.send(HttpRequest.post("echo")
                .body(HttpBodyUtils.form(Map.of("a", "1", "b", "two")))
                .build());
        assertTrue(response.bodyString().contains("a=1"));
        assertTrue(response.bodyString().contains("b=two"));
    }

    @Test
    void modernStreamExposesOneOwnedBodyStream() throws Exception {
        try (HttpStream stream = client.openStream(HttpRequest.get("ok").build())) {
            assertTrue(stream.isSuccessful());
            assertEquals("hello", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
            assertThrows(IllegalStateException.class, stream::body);
        }
    }

    @Test
    void modernSseHasOneTerminalAndCanBeCancelled() throws Exception {
        CountDownLatch terminal = new CountDownLatch(1);
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SseSession session = client.openSse(HttpRequest.get("sse").build(), new SseListener() {
            @Override
            public void onEvent(SseSession current, SseEvent event) {
                events.add(event.data());
            }

            @Override
            public void onTerminal(SseSession current, SseTerminal value) {
                terminal.countDown();
            }
        });

        assertTrue(terminal.await(2, TimeUnit.SECONDS));
        assertEquals(java.util.List.of("first", "second"), events);
        assertTrue(session.isClosed());
        assertEquals(SseTermination.REMOTE_CLOSED, session.terminal().orElseThrow().termination());
        session.close();
        assertFalse(session.isCancelled());
    }

    @Test
    void modernSseSendsDefaultAcceptAndLastEventIdTogether() throws Exception {
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<String> eventData = new AtomicReference<>();
        SseOptions options = SseOptions.builder()
                .idleTimeout(Duration.ofSeconds(3))
                .lastEventId("cursor-42")
                .build();

        client.openSse(HttpRequest.get("sse-headers").build(), options, new SseListener() {
            @Override
            public void onEvent(SseSession session, SseEvent event) {
                eventData.set(event.data());
            }

            @Override
            public void onTerminal(SseSession session, SseTerminal value) {
                terminal.countDown();
            }
        });

        assertTrue(terminal.await(2, TimeUnit.SECONDS));
        assertEquals("text/event-stream|cursor-42", eventData.get());
    }

    private record Message(String message) {
    }
}
