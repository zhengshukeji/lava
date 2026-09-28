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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * 使用 refresh token 换取 access token 的 OAuth 2 认证凭证。
 */
public final class OAuth2RefreshTokenCredential implements MailCredential {
    private final String username;
    private final String clientId;
    private final String refreshToken;
    private final URI tokenEndpoint;
    private final List<String> scopes;
    private final @Nullable String clientSecret;

    /**
     * 创建 OAuth2 refresh token 凭证。
     *
     * <p>token endpoint 必须是无 user-info 和 fragment 的绝对 HTTPS URI。client ID、refresh
     * token 和 client secret 均按不透明值保留首尾空白。</p>
     *
     * @param username      邮箱登录用户名
     * @param clientId      OAuth2 client ID
     * @param refreshToken  refresh token
     * @param tokenEndpoint token endpoint
     * @param scopes        请求的 scope 列表，每项只能包含一个 scope token
     * @param clientSecret  可选 client secret
     */
    public OAuth2RefreshTokenCredential(
            String username,
            String clientId,
            String refreshToken,
            URI tokenEndpoint,
            List<String> scopes,
            @Nullable String clientSecret) {
        this.username = MailValidationUtils.requireNonBlankWithoutControls(username, "username");
        this.clientId = MailValidationUtils.requireNonBlankPreserved(clientId, "clientId");
        this.refreshToken = MailValidationUtils.requireNonBlankPreserved(refreshToken, "refreshToken");
        this.tokenEndpoint = requireHttpsEndpoint(tokenEndpoint);
        ValidationUtils.requireNonNull(scopes, "scopes");
        this.scopes = scopes.stream()
                .map(OAuth2RefreshTokenCredential::requireScope)
                .toList();
        if (this.scopes.isEmpty()) {
            throw new IllegalArgumentException("scopes must not be empty");
        }
        this.clientSecret = clientSecret == null
                ? null
                : MailValidationUtils.requireNonBlankPreserved(clientSecret, "clientSecret");
    }

    @Override
    public String username() {
        return username;
    }

    /**
     * 返回原始 client ID。
     *
     * @return 原始 client ID
     */
    public String clientId() {
        return clientId;
    }

    /**
     * 返回真实 refresh token，仅可用于换取 token，调用方不得记录。
     *
     * @return 原始 refresh token
     */
    public String refreshToken() {
        return refreshToken;
    }

    /**
     * 返回 token endpoint。
     *
     * @return token endpoint
     */
    public URI tokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * 返回不可变的 scope 列表。
     *
     * @return 不可变的 scope 列表
     */
    public List<String> scopes() {
        return scopes;
    }

    /**
     * 返回真实 client secret，仅可用于换取 token，调用方不得记录。
     *
     * @return client secret；未配置时为 {@code null}
     */
    public @Nullable String clientSecret() {
        return clientSecret;
    }

    @Override
    public String toString() {
        return "OAuth2RefreshTokenCredential[username=" + username
                + ", clientId=<redacted>"
                + ", refreshToken=<redacted>, tokenEndpoint=<redacted>, scopes=" + scopes.size()
                + ", clientSecret=<redacted>]";
    }

    private static URI requireHttpsEndpoint(URI endpoint) {
        ValidationUtils.requireNonNull(endpoint, "tokenEndpoint");
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("tokenEndpoint must be an absolute HTTPS URI without user-info or fragment");
        }
        return endpoint;
    }

    private static String requireScope(String scope) {
        String result = MailValidationUtils.requireNonBlank(scope, "scope");
        if (result.codePoints().anyMatch(Character::isWhitespace)
                || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("scope must be a single OAuth scope token");
        }
        return result;
    }
}
