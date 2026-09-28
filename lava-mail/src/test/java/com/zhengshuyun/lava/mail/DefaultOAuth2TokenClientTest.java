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
package com.zhengshuyun.lava.mail;

import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.OkHttpInterop;
import com.zhengshuyun.lava.json.JsonCodec;
import okhttp3.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultOAuth2TokenClientTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void privateClientDisablesRedirectsRetriesCookiesAndApplicationHooks() {
        try (HttpClient http = DefaultOAuth2TokenClient.createPrivateHttpClient()) {
            OkHttpClient nativeClient = OkHttpInterop.unwrap(http);
            assertFalse(nativeClient.followRedirects());
            assertFalse(nativeClient.followSslRedirects());
            assertFalse(nativeClient.retryOnConnectionFailure());
            assertSame(CookieJar.NO_COOKIES, nativeClient.cookieJar());
            assertTrue(nativeClient.interceptors().isEmpty());
            assertTrue(nativeClient.networkInterceptors().isEmpty());
            assertEquals(10_000, nativeClient.connectTimeoutMillis());
            assertEquals(20_000, nativeClient.callTimeoutMillis());
        }
    }

    @Test
    void postsEncodedRefreshFormAndUsesClockForExpiry() {
        try (Exchange exchange = Exchange.respond(
                200, "{\"token_type\":\"Bearer\",\"scope\":\"mail.read mail.send\","
                        + "\"access_token\":\"access-value\",\"expires_in\":300,"
                        + "\"ext_expires_in\":600}")) {
            OAuth2AccessToken token = exchange.client.fetchAccessToken(credential(), CLOCK);

            assertEquals("access-value", token.value());
            assertEquals(Instant.parse("2026-08-17T00:05:00Z"), token.expiresAt());
            Request request = Objects.requireNonNull(exchange.request.get());
            assertEquals("POST", request.method());
            assertEquals("https://login.example.com/oauth/token", request.url().toString());
            assertEquals(Map.of(
                    "grant_type", "refresh_token",
                    "client_id", "client id",
                    "refresh_token", " refresh-secret ",
                    "scope", "mail.read mail.send",
                    "client_secret", " client-secret "), formValues(request));
        }
    }

    @Test
    void doesNotInventExpiryWhenEndpointOmitsIt() {
        try (Exchange exchange = Exchange.respond(200, "{\"access_token\":\"access-value\"}")) {
            OAuth2AccessToken token = exchange.client.fetchAccessToken(credential(), CLOCK);
            assertNull(token.expiresAt());
        }
    }

    @Test
    void mapsHttpStatusWithoutRetainingResponseSecrets() {
        for (int status : List.of(400, 401, 403)) {
            try (Exchange exchange = Exchange.respond(
                    status, "refresh-secret access-secret client-secret")) {
                MailException failure = assertThrows(
                        MailException.class,
                        () -> exchange.client.fetchAccessToken(credential(), CLOCK));
                assertEquals(MailFailureKind.AUTHENTICATION, failure.kind());
                assertSafe(failure);
            }
        }

        try (Exchange exchange = Exchange.respond(500, "access-secret")) {
            MailException failure = assertThrows(
                    MailException.class,
                    () -> exchange.client.fetchAccessToken(credential(), CLOCK));
            assertEquals(MailFailureKind.PROTOCOL, failure.kind());
            assertSafe(failure);
        }
    }

    @Test
    void rejectsMalformedMissingAndInvalidTokenPayloadsWithoutLeakingBodies() {
        for (String body : List.of(
                "{not-json refresh-secret",
                "{}",
                "{\"access_token\":\"   \"}",
                "{\"access_token\":\"bad\\naccess-secret\"}",
                "{\"access_token\":\"access-secret\",\"expires_in\":0}",
                "{\"access_token\":\"access-secret\",\"expires_in\":-1}")) {
            try (Exchange exchange = Exchange.respond(200, body)) {
                MailException failure = assertThrows(
                        MailException.class,
                        () -> exchange.client.fetchAccessToken(credential(), CLOCK));
                assertEquals(MailFailureKind.PARSING, failure.kind());
                assertSafe(failure);
            }
        }
    }

    @Test
    void rejectsOutOfRangeExpiryBeforeCachingToken() {
        Clock nearMaximum = Clock.fixed(Instant.MAX.minusSeconds(1), ZoneOffset.UTC);
        try (Exchange exchange = Exchange.respond(
                200, "{\"access_token\":\"access-value\",\"expires_in\":2}")) {
            MailException failure = assertThrows(
                    MailException.class,
                    () -> exchange.client.fetchAccessToken(credential(), nearMaximum));
            assertEquals(MailFailureKind.PARSING, failure.kind());
        }
    }

    @Test
    void mapsTransportAndResponseLimitFailures() {
        try (Exchange exchange = Exchange.fail(new SSLHandshakeException("access-secret"))) {
            MailException failure = assertThrows(
                    MailException.class,
                    () -> exchange.client.fetchAccessToken(credential(), CLOCK));
            assertEquals(MailFailureKind.TLS, failure.kind());
            assertSafe(failure);
            assertNotNull(failure.getCause());
        }
        try (Exchange exchange = Exchange.fail(new UnknownHostException("refresh-secret"))) {
            MailException failure = assertThrows(
                    MailException.class,
                    () -> exchange.client.fetchAccessToken(credential(), CLOCK));
            assertEquals(MailFailureKind.CONNECTION, failure.kind());
            assertSafe(failure);
        }
        try (Exchange exchange = Exchange.respond(
                200, "x".repeat(DefaultOAuth2TokenClient.MAX_TOKEN_RESPONSE_BYTES + 1))) {
            MailException failure = assertThrows(
                    MailException.class,
                    () -> exchange.client.fetchAccessToken(credential(), CLOCK));
            assertEquals(MailFailureKind.SIZE_LIMIT, failure.kind());
        }
    }

    private static OAuth2RefreshTokenCredential credential() {
        return new OAuth2RefreshTokenCredential(
                "user@example.com", "client id", " refresh-secret ",
                URI.create("https://login.example.com/oauth/token"),
                List.of("mail.read", "mail.send"), " client-secret ");
    }

    private static Map<String, String> formValues(Request request) {
        FormBody form = (FormBody) Objects.requireNonNull(request.body());
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < form.size(); index++) {
            values.put(form.name(index), form.value(index));
        }
        return Map.copyOf(values);
    }

    private static void assertSafe(MailException failure) {
        // 本库生成的消息不得出现凭证；底层异常作为 cause 原样保留，便于排障
        String diagnostic = failure.toString();
        assertFalse(diagnostic.contains("refresh-secret"));
        assertFalse(diagnostic.contains("access-secret"));
        assertFalse(diagnostic.contains("client-secret"));
    }

    private static final class Exchange implements AutoCloseable {
        private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

        private final AtomicReference<Request> request = new AtomicReference<>();
        private final DefaultOAuth2TokenClient client;

        private Exchange(int status, String body, @Nullable IOException failure) {
            OkHttpClient nativeClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request captured = chain.request();
                        request.set(captured);
                        if (failure != null) {
                            throw failure;
                        }
                        return new Response.Builder()
                                .request(captured)
                                .protocol(Protocol.HTTP_1_1)
                                .code(status)
                                .message("local test response")
                                .body(ResponseBody.create(body, JSON))
                                .build();
                    })
                    .build();
            HttpClient http = OkHttpInterop.owned(
                    nativeClient, DefaultOAuth2TokenClient.MAX_TOKEN_RESPONSE_BYTES);
            client = new DefaultOAuth2TokenClient(http, JsonCodec.defaultCodec());
        }

        static Exchange respond(int status, String body) {
            return new Exchange(status, body, null);
        }

        static Exchange fail(IOException failure) {
            return new Exchange(0, "", failure);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
