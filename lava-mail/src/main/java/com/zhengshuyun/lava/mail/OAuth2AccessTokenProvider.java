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

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为单个邮件客户端提供带提前刷新窗口的线程安全 token 缓存。
 */
final class OAuth2AccessTokenProvider implements AutoCloseable {
    private final OAuth2RefreshTokenCredential credential;
    private final OAuth2TokenClient tokenClient;
    private final Clock clock;
    private final Duration refreshAhead;
    private final AtomicBoolean closed = new AtomicBoolean();
    private @Nullable OAuth2AccessToken cached;

    OAuth2AccessTokenProvider(
            OAuth2RefreshTokenCredential credential,
            OAuth2TokenClient tokenClient,
            Clock clock,
            Duration refreshAhead) {
        this.credential = ValidationUtils.requireNonNull(credential, "credential");
        this.tokenClient = ValidationUtils.requireNonNull(tokenClient, "tokenClient");
        this.clock = ValidationUtils.requireNonNull(clock, "clock");
        this.refreshAhead = ValidationUtils.requireNonNull(refreshAhead, "refreshAhead");
        if (refreshAhead.isNegative()) {
            throw new IllegalArgumentException("refreshAhead must not be negative");
        }
    }

    /**
     * 按凭证类型创建令牌提供器：OAuth2 凭证返回新实例，口令凭证不需要令牌返回 null。
     *
     * @param credential 邮箱凭证
     * @param options    客户端选项
     * @return 令牌提供器；口令凭证时为 null
     */
    static @Nullable OAuth2AccessTokenProvider forCredential(
            MailCredential credential, MailClientOptions options) {
        return credential instanceof OAuth2RefreshTokenCredential oauth
                ? new OAuth2AccessTokenProvider(oauth, OAuth2TokenClient.createDefault(),
                options.clock(), options.tokenRefreshAhead())
                : null;
    }

    synchronized String accessToken() {
        if (closed.get()) {
            throw new IllegalStateException("OAuth2 token provider is closed");
        }
        if (cached == null || !cached.reusable(clock, refreshAhead)) {
            // 同步方法把并发刷新合并为一次网络请求，其他调用等待该请求完成。
            cached = tokenClient.fetchAccessToken(credential, clock);
        }
        return cached.value();
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            cached = null;
            tokenClient.close();
        }
    }
}
