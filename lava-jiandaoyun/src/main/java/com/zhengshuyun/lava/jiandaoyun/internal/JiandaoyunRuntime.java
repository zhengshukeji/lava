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

package com.zhengshuyun.lava.jiandaoyun.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简道云根客户端及各功能入口共享的运行时。
 *
 * <p>运行时集中管理协议传输层、HTTP 客户端所有权和根客户端关闭状态。各功能入口仅持有该对象，
 * 每次操作先取得仍可用的传输层，从而避免在客户端层级之间分别传递传输层和存活检查回调。</p>
 */
public final class JiandaoyunRuntime implements AutoCloseable {
    /** 负责鉴权、发送和错误解析的共享协议层。 */
    private final JiandaoyunTransport transport;
    /** 传输层实际使用的 HTTP 客户端。 */
    private final HttpClient httpClient;
    /** 是否由当前运行时负责关闭 HTTP 客户端。 */
    private final boolean ownsHttpClient;
    /** 根客户端关闭状态，保证关闭操作幂等并拒绝后续调用。 */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建简道云共享运行时。
     *
     * @param transport 共享协议传输层
     * @param httpClient 传输层使用的 HTTP 客户端
     * @param ownsHttpClient 是否负责关闭 HTTP 客户端
     */
    public JiandaoyunRuntime(JiandaoyunTransport transport,
                             HttpClient httpClient,
                             boolean ownsHttpClient) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
        this.httpClient = ValidationUtils.requireNonNull(httpClient, "httpClient");
        this.ownsHttpClient = ownsHttpClient;
    }

    /**
     * 返回仍可使用的协议传输层。
     *
     * @return 共享协议传输层
     * @throws IllegalStateException 根客户端已经关闭
     */
    public JiandaoyunTransport transport() {
        ensureOpen();
        return transport;
    }

    /**
     * 确保根客户端仍处于可用状态。
     *
     * @throws IllegalStateException 根客户端已经关闭
     */
    public void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("JiandaoyunClient is closed");
        }
    }

    /**
     * 关闭共享运行时并清除敏感信息。仅关闭由运行时拥有的 HTTP 客户端。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.clearSecret();
            if (ownsHttpClient) {
                httpClient.close();
            }
        }
    }
}
