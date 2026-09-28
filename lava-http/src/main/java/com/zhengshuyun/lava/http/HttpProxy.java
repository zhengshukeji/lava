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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import okhttp3.*;
import okhttp3.Authenticator;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.List;

/**
 * HTTP 或 SOCKS 代理配置；其公开 API 仅使用 JDK 类型。
 *
 * @author Toint
 * @since 2026/1/9
 */
public final class HttpProxy {

    private final @Nullable ProxySelector proxySelector;
    private final @Nullable Authenticator authenticator;

    private HttpProxy(Builder builder) {
        this.proxySelector = builder.proxySelector;
        this.authenticator = builder.authenticator;
    }

    /**
     * 获取代理选择器
     *
     * @return 代理选择器, 未配置时返回 null
     */
    public @Nullable ProxySelector proxySelector() {
        return proxySelector;
    }

    /**
     * 获取代理认证器
     * <p>
     * 包内可见: 返回类型是 {@code okhttp3.Authenticator}, 不应出现在公开 API 上,
     * 否则更换传输层实现即成为对外破坏性变更. 仅 {@link HttpClient} 装配时使用.
     *
     * @return 代理认证器, 未配置时返回 null
     */
    @Nullable Authenticator authenticator() {
        return authenticator;
    }

    /**
     * 创建固定代理 (无认证)
     */
    public static HttpProxy of(String host, int port) {
        return builder()
                .http(host, port)
                .build();
    }

    /**
     * 创建固定代理 (带认证)
     */
    public static HttpProxy of(String host, int port, String username, String password) {
        return builder()
                .http(host, port)
                .auth(username, password)
                .build();
    }

    /**
     * 创建 SOCKS 代理
     */
    public static HttpProxy socks(String host, int port) {
        return builder()
                .socks(host, port)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private @Nullable ProxySelector proxySelector;
        private @Nullable Authenticator authenticator;

        /**
         * 设置 HTTP 代理
         */
        public Builder http(String host, int port) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            this.proxySelector = new FixedProxySelector(proxy);
            return this;
        }

        /**
         * 设置 SOCKS 代理
         */
        public Builder socks(String host, int port) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
            this.proxySelector = new FixedProxySelector(proxy);
            return this;
        }

        /**
         * 设置代理认证
         */
        public Builder auth(String username, String password) {
            this.authenticator = new ProxyAuthenticator(username, password);
            return this;
        }

        /**
         * 自定义 ProxySelector
         */
        public Builder proxySelector(ProxySelector proxySelector) {
            this.proxySelector = proxySelector;
            return this;
        }

        /**
         * 自定义 Authenticator
         * <p>
         * 包内可见: 参数类型是 {@code okhttp3.Authenticator}, 不应出现在公开 API 上,
         * 否则更换传输层实现即成为对外破坏性变更.
         * 常规的用户名密码代理认证请用 {@link HttpProxy#of(String, int, String, String)}.
         */
        Builder authenticator(Authenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        public HttpProxy build() {
            return new HttpProxy(this);
        }
    }

    /**
     * 固定代理选择器
     */
    private static class FixedProxySelector extends ProxySelector {

        private final Proxy proxy;

        FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(URI uri) {
            return List.of(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // 连接失败时的处理 (可以记录日志)
        }
    }

    /**
     * 代理认证器
     */
    private static class ProxyAuthenticator implements Authenticator {

        /**
         * HTTP 407：需要代理身份验证。
         */
        private static final int HTTP_PROXY_AUTH = 407;

        /**
         * 同一条请求链上最多尝试的认证次数, 防止凭据错误时无限重试
         */
        private static final int MAX_AUTH_ATTEMPTS = 3;

        private final String username;
        private final String password;

        ProxyAuthenticator(String username, String password) {
            ValidationUtils.requireNonNull(username, "username must not be null");
            ValidationUtils.requireNonNull(password, "password must not be null");
            this.username = username;
            this.password = password;
        }

        @Override
        public @Nullable Request authenticate(@Nullable Route route, Response response) throws IOException {
            // 只处理代理认证 (407) , 返回 null 表示放弃认证
            if (response.code() != HTTP_PROXY_AUTH) {
                return null;
            }

            // 凭据错误时代理会持续返回 407, 必须限制尝试次数避免无限重试
            if (responseCount(response) >= MAX_AUTH_ATTEMPTS) {
                return null;
            }

            String credential = Credentials.basic(username, password);
            return response.request().newBuilder()
                    .header(HttpHeaderNames.PROXY_AUTHORIZATION, credential)
                    .build();
        }

        private int responseCount(Response response) {
            int result = 1;
            for (Response prior = response.priorResponse(); prior != null; prior = prior.priorResponse()) {
                result++;
            }
            return result;
        }
    }
}
