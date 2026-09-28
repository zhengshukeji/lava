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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpHeadersTest {

    @Test
    void preservesOrderAndUsesCaseInsensitiveLookup() {
        HttpHeaders headers = HttpHeaders.builder()
                .add("X-Test", "one")
                .add("x-test", "two")
                .add("Accept", "text/plain")
                .build();

        assertEquals("two", headers.get("X-TEST"));
        assertEquals(List.of("one", "two"), headers.values("x-Test"));
        assertEquals(List.of("X-Test", "Accept"), List.copyOf(headers.names()));
        assertTrue(headers.contains("accept"));
        assertFalse(headers.contains("missing"));
        assertEquals(3, headers.size());
        assertEquals("X-Test", headers.name(0));
        assertEquals("one", headers.value(0));
    }

    @Test
    void builderSetRemoveAndSnapshotsAreIndependent() {
        HttpHeaders.Builder builder = HttpHeaders.builder()
                .add("X-A", "1")
                .add("x-a", "2")
                .addAll(Map.of("X-B", "3"));
        HttpHeaders first = builder.set("X-A", "4").build();
        builder.remove("x-a").addAll(HttpHeaders.of("X-C", "5"));

        assertEquals(List.of("4"), first.values("x-a"));
        assertFalse(builder.build().contains("X-A"));
        assertEquals("5", builder.build().get("X-C"));
        assertThrows(IndexOutOfBoundsException.class, () -> first.name(2));
        assertThrows(IndexOutOfBoundsException.class, () -> first.value(-1));
    }

    @Test
    void validatesNamesAndValuesBeforeTransport() {
        for (String name : List.of("", "Bad Name", "Bad:Name", "X-坏", "X\rInjected")) {
            assertThrows(IllegalArgumentException.class, () -> HttpHeaders.of(name, "value"));
        }
        for (String value : List.of("line\rbreak", "line\nbreak", "nul\0byte", "del\u007f", "中文")) {
            assertThrows(IllegalArgumentException.class, () -> HttpHeaders.of("X-Test", value));
        }
        assertEquals("a\tb", HttpHeaders.of("X-Test", "a\tb").get("x-test"));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaders.of((String[]) null));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaders.of("X"));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaders.of("X", null));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaders.builder().addAll((Map<String, String>) null));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaders.builder().addAll((HttpHeaders) null));
    }

    @Test
    void redactsCredentialsInEveryDiagnosticRendering() {
        HttpHeaders headers = HttpHeaders.of(
                "Authorization", "Bearer super-secret",
                "X-Api-Key", "api-value",
                "clientSecret", "client-value",
                "X-Test", "visible");

        String text = headers.toString();
        assertFalse(text.contains("super-secret"));
        assertFalse(text.contains("api-value"));
        assertFalse(text.contains("client-value"));
        assertTrue(text.contains("visible"));
        assertEquals("[REDACTED]", headers.redacted().get("authorization"));
        // 原始请求头仍可读取，只有诊断快照和字符串表示会脱敏。
        assertEquals("Bearer super-secret", headers.get("Authorization"));
    }

    @Test
    void redactsSensitiveQueriesInsideUrlValuedHeadersWithoutSubstringFalsePositives() {
        HttpHeaders headers = HttpHeaders.of(
                "Location", "https://example.test/next?accessToken=location-secret&tokenizer=visible"
                        + "#idToken=fragment-secret",
                "Referer", "/source?clientSecret=referer-secret&secretary=public",
                "Content-Location", "https://user:password@example.test/item?refreshToken=content-secret",
                "X-Tokenizer", "not-sensitive",
                "X-Secretariat", "also-visible");

        HttpHeaders redacted = headers.redacted();
        for (String secret : List.of(
                "location-secret", "referer-secret", "password@example", "content-secret",
                "fragment-secret")) {
            assertFalse(redacted.toString().contains(secret), secret);
            assertFalse(headers.toString().contains(secret), secret);
        }
        assertTrue(redacted.get("Location").contains("tokenizer=visible"));
        assertTrue(redacted.get("Referer").startsWith("/source?"));
        assertTrue(redacted.get("Referer").contains("secretary=public"));
        assertEquals("not-sensitive", redacted.get("X-Tokenizer"));
        assertEquals("also-visible", redacted.get("X-Secretariat"));

        // 原始访问器仍返回传输数据，只有诊断快照会脱敏
        assertTrue(headers.get("Location").contains("location-secret"));
    }

    @Test
    void sensitiveNameRuleAvoidsSubstringFalsePositives() {
        assertTrue(HttpRedactionUtils.isSensitiveName("accessToken"));
        assertTrue(HttpRedactionUtils.isSensitiveName("client_secret"));
        assertTrue(HttpRedactionUtils.isSensitiveName("X-Api-Key"));
        assertTrue(HttpRedactionUtils.isSensitiveName("Set-Cookie"));
        assertTrue(HttpRedactionUtils.isSensitiveName("Proxy-Authorization"));
        assertFalse(HttpRedactionUtils.isSensitiveName("tokenizer"));
        assertFalse(HttpRedactionUtils.isSensitiveName("secretary"));
        assertEquals("no-query", HttpRedactionUtils.redactHeaderValue("Location", "no-query"));
        assertEquals("[invalid URL]", HttpRedactionUtils.redactUrl("ftp://user:password@example.test/"));
    }

    @Test
    void safelyRoundTripsOkHttpHeaders() {
        okhttp3.Headers nativeHeaders = new okhttp3.Headers.Builder()
                .add("Set-Cookie", "a=1")
                .add("Set-Cookie", "b=2")
                .build();
        HttpHeaders lava = HttpHeaders.fromOkHttp(nativeHeaders);

        assertEquals(List.of("a=1", "b=2"), lava.values("set-cookie"));
        assertEquals(nativeHeaders, lava.toOkHttp());
        assertEquals(lava, HttpHeaders.of("Set-Cookie", "a=1", "Set-Cookie", "b=2"));
        assertEquals(lava.hashCode(), HttpHeaders.fromOkHttp(nativeHeaders).hashCode());
        assertNotEquals(lava, HttpHeaders.of("set-cookie", "a=1", "set-cookie", "b=2"));
        assertTrue(HttpHeaders.of().isEmpty());
        assertNull(HttpHeaders.of().get("Missing"));
        assertArrayEquals(new String[]{"a=1", "b=2"}, lava.values("Set-Cookie").toArray(String[]::new));
    }

    @Test
    void requestAndUrlDiagnosticsHideSecrets() {
        HttpRequest request = HttpRequest.get(
                        "https://user:password@example.test/path?token=abc&q=visible&client_secret=def")
                .bearerToken("credential")
                .build();

        String text = request.toString();
        assertFalse(text.contains("password"));
        assertFalse(text.contains("abc"));
        assertFalse(text.contains("def"));
        assertFalse(text.contains("credential"));
        assertTrue(text.contains("visible"));
    }
}
