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
import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import java.util.function.Consumer;

/**
 * 暴露 OkHttp 类型的唯一公开边界。
 *
 * <p>常规 HTTP API 保持传输中立；外部客户端、拦截器、CookieJar 和原生请求体
 * 统一通过此类显式接入。</p>
 */
public final class OkHttpInterop {
    private OkHttpInterop() {
    }

    /**
     * 包装外部客户端，但不接管其 dispatcher、连接池或缓存。
     *
     * @param client 外部管理生命周期的 OkHttp 客户端
     * @return 不拥有底层资源的 Lava 客户端
     */
    public static HttpClient borrowed(OkHttpClient client) {
        return borrowed(client, HttpClient.DEFAULT_MAX_BUFFERED_RESPONSE_BYTES);
    }

    /**
     * 包装外部客户端，并设置缓冲响应大小上限。
     *
     * @param client                   外部管理生命周期的 OkHttp 客户端
     * @param maxBufferedResponseBytes 缓冲响应正文的最大字节数
     * @return 不拥有底层资源的 Lava 客户端
     */
    public static HttpClient borrowed(OkHttpClient client, int maxBufferedResponseBytes) {
        return new HttpClient(client, false, maxBufferedResponseBytes);
    }

    /**
     * 包装外部客户端，并负责关闭其资源。
     *
     * @param client 由返回客户端关闭的 OkHttp 客户端
     * @return 拥有底层资源的 Lava 客户端
     */
    public static HttpClient owned(OkHttpClient client) {
        return owned(client, HttpClient.DEFAULT_MAX_BUFFERED_RESPONSE_BYTES);
    }

    /**
     * 包装外部客户端并接管其传输资源，同时设置缓冲响应大小上限。
     *
     * @param client                   由返回客户端关闭的 OkHttp 客户端
     * @param maxBufferedResponseBytes 缓冲响应正文的最大字节数
     * @return 拥有底层资源的 Lava 客户端
     */
    public static HttpClient owned(OkHttpClient client, int maxBufferedResponseBytes) {
        return new HttpClient(client, true, maxBufferedResponseBytes);
    }

    /**
     * 向客户端构建器添加 OkHttp 拦截器。
     *
     * @param builder     Lava 客户端构建器
     * @param interceptor OkHttp 拦截器
     * @return 原构建器
     */
    public static HttpClient.Builder addInterceptor(HttpClient.Builder builder, Interceptor interceptor) {
        return customize(builder, okHttp -> okHttp.addInterceptor(interceptor));
    }

    /**
     * 为客户端构建器设置 Cookie 存储。
     *
     * @param builder   Lava 客户端构建器
     * @param cookieJar OkHttp Cookie 存储
     * @return 原构建器
     */
    public static HttpClient.Builder cookieJar(HttpClient.Builder builder, CookieJar cookieJar) {
        return customize(builder, okHttp -> okHttp.cookieJar(cookieJar));
    }

    /**
     * 在构建前执行底层 OkHttp 定制。
     *
     * @param builder    Lava 客户端构建器
     * @param customizer 操作底层构建器的回调
     * @return 原构建器
     */
    public static HttpClient.Builder customize(HttpClient.Builder builder,
                                               Consumer<OkHttpClient.Builder> customizer) {
        return ValidationUtils.requireNonNull(builder, "builder must not be null")
                .customizeOkHttp(ValidationUtils.requireNonNull(customizer, "customizer must not be null"));
    }

    /**
     * 为请求构建器设置 OkHttp 原生请求体。
     *
     * @param builder Lava 请求构建器
     * @param body    OkHttp 请求体
     * @return 原构建器
     */
    public static HttpRequest.Builder requestBody(HttpRequest.Builder builder, RequestBody body) {
        return ValidationUtils.requireNonNull(builder, "builder must not be null")
                .okHttpBody(ValidationUtils.requireNonNull(body, "body must not be null"));
    }

    /**
     * 返回底层客户端以供高级诊断；不会转移所有权。
     *
     * @param client Lava 客户端
     * @return 底层 OkHttp 客户端
     */
    public static OkHttpClient unwrap(HttpClient client) {
        return ValidationUtils.requireNonNull(client, "client must not be null").okHttpClient();
    }
}
