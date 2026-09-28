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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiTest {

    @Test
    void requestFactoriesMethodsHeadersAndQueryParametersAreStable() {
        HttpHeaders headers = HttpHeaders.of("X-Test", "one");
        HttpRequest request = HttpRequest.get("https://example.test/path?a=old&a=older")
                .queryParam("a", "new")
                .addQueryParam("flag", null)
                .addQueryParams(Map.of("space", "a b"))
                .headers(headers)
                .addHeader("X-Other", "two")
                .userAgent("agent")
                .cookie("a=1")
                .build();

        assertEquals(HttpMethod.GET, request.method());
        assertTrue(request.url().contains("a=new"));
        assertFalse(request.url().contains("a=old"));
        assertTrue(request.url().contains("flag"));
        assertTrue(request.url().contains("space=a%20b"));
        assertEquals("one", request.headers().get("X-Test"));
        assertEquals("two", request.headers().get("X-Other"));
        assertEquals("agent", request.headers().get("User-Agent"));
        assertEquals("a=1", request.headers().get("Cookie"));

        assertEquals(HttpMethod.POST, HttpRequest.post("https://example.test").build().method());
        assertEquals(HttpMethod.PUT, HttpRequest.put("https://example.test").build().method());
        assertEquals(HttpMethod.DELETE, HttpRequest.delete("https://example.test").build().method());
        assertEquals(HttpMethod.PATCH, HttpRequest.patch("https://example.test").build().method());
        assertEquals(HttpMethod.HEAD, HttpRequest.head("https://example.test").build().method());
        assertEquals(HttpMethod.GET,
                HttpRequest.get("https://example.test", StandardCharsets.UTF_16).build().method());
        assertEquals(HttpMethod.POST,
                HttpRequest.post("https://example.test", StandardCharsets.UTF_16).build().method());
        assertEquals(HttpMethod.PUT,
                HttpRequest.put("https://example.test", StandardCharsets.UTF_16).build().method());
        assertEquals(HttpMethod.DELETE,
                HttpRequest.delete("https://example.test", StandardCharsets.UTF_16).build().method());
        assertEquals(HttpMethod.PATCH,
                HttpRequest.patch("https://example.test", StandardCharsets.UTF_16).build().method());
        assertEquals(HttpMethod.HEAD,
                HttpRequest.head("https://example.test", StandardCharsets.UTF_16).build().method());

        HttpRequest relative = HttpRequest.get("echo")
                .addQueryParam("q", "a b")
                .build();
        assertEquals("http://example.test/api/echo?q=a%20b",
                relative.resolvedUrl(URI.create("http://example.test/api/")));
        assertEquals("http://example.test/api/echo?q=a%20b",
                relative.withHeader("X-Test", "value")
                        .resolvedUrl(URI.create("http://example.test/api/")));
        HttpRequest relativeWithBody = HttpRequest.post("echo")
                .addQueryParam("q", "a b")
                .build()
                .withBody(HttpBodyUtils.text("body"));
        assertEquals("http://example.test/api/echo?q=a%20b",
                relativeWithBody.resolvedUrl(URI.create("http://example.test/api/")));
        HttpRequest queryOnly = HttpRequest.get("?existing=1")
                .addQueryParam("q", "a b")
                .build();
        assertEquals("http://example.test/api/?existing=1&q=a%20b",
                queryOnly.resolvedUrl(URI.create("http://example.test/api/")));
    }

    @Test
    void requestBodiesUseSafeLavaEntryPoints(@TempDir Path tempDirectory) throws IOException {
        assertNotNull(HttpRequest.post("https://example.test").jsonBody("{}").build().toOkHttpRequest().body());
        assertNotNull(HttpRequest.post("https://example.test").xmlBody("<a/>").build().toOkHttpRequest().body());
        assertNotNull(HttpRequest.post("https://example.test").textBody("x").build().toOkHttpRequest().body());
        assertNotNull(HttpRequest.post("https://example.test")
                .body(new byte[]{1}, "application/octet-stream").build().toOkHttpRequest().body());
        assertNotNull(HttpRequest.post("https://example.test")
                .body("body", "text/custom").build().toOkHttpRequest().body());
        assertNotNull(HttpRequest.post("https://example.test")
                .formBody(Map.of("a", "b")).build().toOkHttpRequest().body());
        // 强制要求请求体的方法会获得一个显式空请求体。
        assertNotNull(HttpRequest.post("https://example.test").build().toOkHttpRequest().body());
        assertNull(HttpRequest.delete("https://example.test").build().toOkHttpRequest().body());

        assertThrows(IllegalStateException.class, () -> HttpRequest.get("https://example.test")
                .textBody("not allowed").build().toOkHttpRequest());

        Path file = Files.writeString(tempDirectory.resolve("upload.txt"), "contents");
        HttpRequest.MultipartBuilder multipart = HttpRequest.MultipartBuilder.builder()
                .addFormField("field", "value")
                .addFile("path", file)
                .addFile("typed", file, "text/plain")
                .addFile("legacy", file.toFile())
                .addFile("legacy-typed", file.toFile(), "text/plain")
                .addFile("bytes", "file.bin", new byte[]{1, 2})
                .addFile("bytes-typed", null, new byte[]{3}, "application/octet-stream");
        assertNotNull(HttpRequest.post("https://example.test")
                .multipartBody(multipart).build().toOkHttpRequest().body());
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.MultipartBuilder.builder().addFile("x", tempDirectory.resolve("missing")));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.MultipartBuilder.builder().addFile("x", tempDirectory.toFile()));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.MultipartBuilder.builder().addFile("x", "x", null));
    }

    @Test
    void requestValidationFailsBeforeNetworkActivity() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(" "));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.builder("https://example.test", null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.builder("https://example.test", HttpMethod.GET, null));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get("ftp://example.test").build());
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.get("https://example.test").addQueryParam(" ", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.get("https://example.test").queryParam(null, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.get("https://example.test").addQueryParams(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.get("https://example.test").headers(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").formBody(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").multipartBody(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").body((String) null, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").body((byte[]) null, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").bearerToken(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.post("https://example.test").basicAuth(null, "x"));
    }

    @Test
    void httpMethodIsLocaleIndependentAndSupportsExtensions() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertSame(HttpMethod.OPTIONS, HttpMethod.valueOf("options"));
        } finally {
            Locale.setDefault(original);
        }
        HttpMethod custom = HttpMethod.valueOf("propfind");
        assertEquals("PROPFIND", custom.name());
        assertTrue(custom.permitsRequestBody());
        assertFalse(HttpMethod.GET.permitsRequestBody());
        assertTrue(HttpMethod.PATCH.requiresRequestBody());
        assertFalse(HttpMethod.DELETE.requiresRequestBody());
        assertTrue(Arrays.asList(HttpMethod.values()).contains(HttpMethod.TRACE));
        assertEquals(HttpMethod.GET, HttpMethod.valueOf("GET"));
        assertEquals(HttpMethod.HEAD, HttpMethod.valueOf("head"));
        assertEquals(HttpMethod.POST, HttpMethod.valueOf("post"));
        assertEquals(HttpMethod.PUT, HttpMethod.valueOf("put"));
        assertEquals(HttpMethod.PATCH, HttpMethod.valueOf("patch"));
        assertEquals(HttpMethod.DELETE, HttpMethod.valueOf("delete"));
        assertEquals(HttpMethod.TRACE, HttpMethod.valueOf("trace"));
        assertEquals("GET", HttpMethod.GET.toString());
        assertNotEquals(HttpMethod.GET, custom);
        assertEquals(HttpMethod.GET.hashCode(), HttpMethod.valueOf("get").hashCode());
        assertThrows(IllegalArgumentException.class, () -> HttpMethod.valueOf(" "));
    }

    @Test
    void callOptionsAndClientBuilderValidateOwnershipRelevantConfiguration() {
        RequestOptions defaults = RequestOptions.defaults();
        assertTrue(defaults.isDefault());
        RequestOptions options = RequestOptions.builder()
                .connectTimeout(Duration.ZERO)
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .callTimeout(Duration.ZERO)
                .build();
        assertFalse(options.isDefault());
        assertEquals(Duration.ZERO, options.connectTimeout());
        assertEquals(Duration.ZERO, options.readTimeout());
        assertEquals(Duration.ZERO, options.writeTimeout());
        assertEquals(Duration.ZERO, options.callTimeout());
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().callTimeout(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> RequestOptions.builder().readTimeout(null));

        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().connectTimeout(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().readTimeout(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().connectionPool(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().connectionPool(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().proxy(null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().maxBufferedResponseBytes(-1));
        assertThrows(IllegalArgumentException.class,
                () -> HttpClient.builder().maxBufferedResponseBytes(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> OkHttpInterop.borrowed(null));
        assertThrows(IllegalArgumentException.class,
                () -> OkHttpInterop.customize(null, nativeBuilder -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> OkHttpInterop.customize(HttpClient.builder(), null));
        assertThrows(IllegalArgumentException.class, () -> OkHttpInterop.unwrap(null));
        assertThrows(IllegalArgumentException.class,
                () -> OkHttpInterop.requestBody(null, RequestBodyFactory.empty()));
    }

    @Test
    void proxyFactoriesKeepNativeAuthenticatorInternal() throws Exception {
        HttpProxy plain = HttpProxy.of("127.0.0.1", 8080);
        assertNotNull(plain.proxySelector());
        assertNull(plain.authenticator());
        assertEquals(Proxy.Type.HTTP,
                plain.proxySelector().select(new java.net.URI("http://example.test")).getFirst().type());

        HttpProxy authenticated = HttpProxy.of("127.0.0.1", 8080, "user", "password");
        assertNotNull(authenticated.authenticator());
        HttpProxy socks = HttpProxy.socks("127.0.0.1", 1080);
        assertEquals(Proxy.Type.SOCKS,
                socks.proxySelector().select(new java.net.URI("http://example.test")).getFirst().type());
        HttpProxy custom = HttpProxy.builder()
                .proxySelector(ProxySelectorFactory.direct())
                .authenticator(authenticated.authenticator())
                .build();
        assertNotNull(custom.proxySelector());
        assertNotNull(custom.authenticator());
    }

    @Test
    void metadataRequiresCompleteNonNegativeInputAndRedacts() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        HttpCallMetadata metadata = new HttpCallMetadata(
                "id", "GET", "https://example.test?q=ok&token=secret",
                start, start.plusSeconds(1), Duration.ofSeconds(1),
                HttpHeaders.of(
                        "Authorization", "secret",
                        "Referer", "https://example.test/source?clientSecret=referer-secret"),
                HttpHeaders.of(
                        "Set-Cookie", "secret",
                        "Location", "https://example.test/next?accessToken=location-secret"),
                "http/1.1", 404, "Not Found");

        assertFalse(metadata.url().contains("secret"));
        assertEquals("[REDACTED]", metadata.requestHeaders().get("Authorization"));
        assertEquals("[REDACTED]", metadata.responseHeaders().get("Set-Cookie"));
        assertFalse(metadata.requestHeaders().get("Referer").contains("referer-secret"));
        assertFalse(metadata.responseHeaders().get("Location").contains("location-secret"));
        assertFalse(metadata.toString().contains("referer-secret"));
        assertFalse(metadata.toString().contains("location-secret"));
        assertEquals("Not Found", metadata.statusMessage());
        assertFalse(metadata.isSuccessful());
        assertThrows(IllegalArgumentException.class, () -> new HttpCallMetadata(
                "id", "GET", "https://example.test", start, start, Duration.ofNanos(-1),
                HttpHeaders.of(), HttpHeaders.of(), "http/1.1", 200, "OK"));
    }

    @Test
    void failureClassifierUsesStableKinds() {
        assertEquals(HttpFailureKind.CANCELLED, HttpClient.classify(new IOException(), true));
        assertEquals(HttpFailureKind.TIMEOUT,
                HttpClient.classify(new SocketTimeoutException(), false));
        assertEquals(HttpFailureKind.PROTOCOL,
                HttpClient.classify(new RuntimeException(new ProtocolException()), false));
        assertEquals(HttpFailureKind.IO, HttpClient.classify(new IOException(), false));
    }

    @Test
    void invalidSseRequestFailsBeforeDeliveringTerminalCallback() {
        AtomicBoolean terminalDelivered = new AtomicBoolean();
        try (HttpClient client = HttpClient.builder().build()) {
            assertThrows(IllegalArgumentException.class, () -> client.openSse(
                    HttpRequest.get("relative-without-base").build(), new SseListener() {
                        @Override
                        public void onTerminal(SseSession session, SseTerminal terminal) {
                            terminalDelivered.set(true);
                        }
                    }));
        }
        assertFalse(terminalDelivered.get());
    }

    /**
     * 避免在参数校验断言中混入无关的构造细节。
     */
    private static final class RequestBodyFactory {
        private RequestBodyFactory() {
        }

        static okhttp3.RequestBody empty() {
            return okhttp3.RequestBody.create(new byte[0]);
        }
    }

    /**
     * 创建一个行为确定的直连 ProxySelector。
     */
    private static final class ProxySelectorFactory {
        private ProxySelectorFactory() {
        }

        static java.net.ProxySelector direct() {
            return new java.net.ProxySelector() {
                @Override
                public java.util.List<Proxy> select(java.net.URI uri) {
                    return java.util.List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(java.net.URI uri, java.net.SocketAddress address,
                                          IOException exception) {
                }
            };
        }
    }
}
