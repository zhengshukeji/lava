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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.*;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用模块私有 HTTP 客户端执行凭证安全的 refresh token 交换。
 */
final class DefaultOAuth2TokenClient implements OAuth2TokenClient {
    static final int MAX_TOKEN_RESPONSE_BYTES = 64 * 1024;

    private final HttpClient http;
    private final JsonCodec json;

    DefaultOAuth2TokenClient() {
        this(createPrivateHttpClient(), JsonCodec.defaultCodec());
    }

    static HttpClient createPrivateHttpClient() {
        // token 请求不继承应用拦截器、Cookie、连接池和重试策略，避免凭证跨边界传播。
        return HttpClient.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(20))
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .maxBufferedResponseBytes(MAX_TOKEN_RESPONSE_BYTES)
                .build();
    }

    DefaultOAuth2TokenClient(HttpClient http, JsonCodec json) {
        this.http = ValidationUtils.requireNonNull(http, "http");
        this.json = ValidationUtils.requireNonNull(json, "json");
    }

    @Override
    public OAuth2AccessToken fetchAccessToken(
            OAuth2RefreshTokenCredential credential, Clock clock) {
        ValidationUtils.requireNonNull(credential, "credential");
        ValidationUtils.requireNonNull(clock, "clock");
        HttpRequest request = HttpRequest.post(credential.tokenEndpoint().toString())
                .formBody(form(credential))
                .build();
        HttpResponse response;
        try {
            response = http.send(request);
            // 错误响应体可能含敏感诊断信息，因此状态失败时不读取也不保留正文。
            if (!response.isSuccessful()) {
                MailFailureKind kind = response.statusCode() == 400
                        || response.statusCode() == 401
                        || response.statusCode() == 403
                        ? MailFailureKind.AUTHENTICATION
                        : MailFailureKind.PROTOCOL;
                throw new MailException(
                        kind, "OAuth2 token endpoint rejected refresh request with HTTP status "
                        + response.statusCode());
            }
            OAuth2TokenResponse payload;
            try {
                payload = json.read(response.bodyString(), OAuth2TokenResponse.class);
            } catch (JsonException exception) {
                // Jackson 诊断可能引用响应原文，因此不能把异常对象保留到 cause 链中。
                throw new MailException(MailFailureKind.PARSING, "OAuth2 token response is not valid JSON");
            }
            if (payload.accessToken() == null || payload.accessToken().isBlank()) {
                throw new MailException(
                        MailFailureKind.PARSING, "OAuth2 token response is missing access_token");
            }
            Instant expiresAt = expiry(clock, payload.expiresIn());
            try {
                return new OAuth2AccessToken(payload.accessToken(), expiresAt);
            } catch (IllegalArgumentException exception) {
                throw new MailException(
                        MailFailureKind.PARSING, "OAuth2 token response has an invalid access_token", exception);
            }
        } catch (MailException exception) {
            throw exception;
        } catch (HttpException exception) {
            throw new MailException(
                    mapHttpFailure(exception.getKind()), "OAuth2 token request failed", exception);
        }
    }

    @Override
    public void close() {
        http.close();
    }

    private static Map<String, String> form(OAuth2RefreshTokenCredential credential) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("grant_type", "refresh_token");
        values.put("client_id", credential.clientId());
        values.put("refresh_token", credential.refreshToken());
        values.put("scope", String.join(" ", credential.scopes()));
        if (credential.clientSecret() != null) {
            values.put("client_secret", credential.clientSecret());
        }
        return Map.copyOf(values);
    }

    private static @Nullable Instant expiry(Clock clock, @Nullable Long expiresIn) {
        if (expiresIn == null) {
            return null;
        }
        if (expiresIn <= 0) {
            throw new MailException(MailFailureKind.PARSING, "OAuth2 token response has invalid expires_in");
        }
        try {
            return clock.instant().plusSeconds(expiresIn);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new MailException(MailFailureKind.PARSING, "OAuth2 token expiry is out of range", exception);
        }
    }

    private static MailFailureKind mapHttpFailure(HttpFailureKind kind) {
        return switch (kind) {
            case DNS, CONNECTION, IO -> MailFailureKind.CONNECTION;
            case TLS -> MailFailureKind.TLS;
            case TIMEOUT -> MailFailureKind.TIMEOUT;
            case RESPONSE_TOO_LARGE -> MailFailureKind.SIZE_LIMIT;
            case CANCELLED, PROTOCOL -> MailFailureKind.PROTOCOL;
        };
    }

    private record OAuth2TokenResponse(
            @JsonAlias("access_token") @Nullable String accessToken,
            @JsonAlias("expires_in") @Nullable Long expiresIn) {
    }
}
