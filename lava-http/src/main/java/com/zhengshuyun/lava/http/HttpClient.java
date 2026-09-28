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

import com.zhengshuyun.lava.core.id.IdUtils;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.json.JsonCodec;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 实例作用域的同步 HTTP 和 SSE 客户端。
 *
 * <p>通过 {@link #builder()} 创建的客户端拥有其传输资源。外部 OkHttp 客户端只能通过
 * {@link OkHttpInterop} 包装，并明确选择借入或拥有语义；关闭包装器只会取消由该包装器创建的调用。</p>
 */
public final class HttpClient implements AutoCloseable {
    /**
     * 默认缓冲响应正文的最大字节数。
     */
    public static final int DEFAULT_MAX_BUFFERED_RESPONSE_BYTES = 16 * 1024 * 1024;
    /**
     * 失败 SSE 握手中允许保留的最大响应正文字节数。
     */
    private static final long MAX_SSE_FAILURE_BODY_BYTES = 1024 * 1024L;

    /**
     * 执行 HTTP 调用的底层客户端。
     */
    private final OkHttpClient okHttpClient;
    /**
     * 是否由当前实例关闭底层连接池、调度器和缓存。
     */
    private final boolean ownsResources;
    /**
     * 默认的缓冲响应正文大小上限。
     */
    private final int maxBufferedResponseBytes;
    /**
     * 相对 URL 的解析基准；未配置时为 null。
     */
    private final @Nullable URI baseUrl;
    /**
     * 每个请求都会合并的默认请求头。
     */
    private final HttpHeaders defaultHeaders;
    /**
     * JSON 请求和响应使用的编解码器。
     */
    private final JsonCodec jsonCodec;
    /**
     * 简洁 SSE API 使用的默认读取空闲超时。
     */
    private final Duration sseIdleTimeout;
    /**
     * 客户端关闭标记，阻止新调用加入活动集合。
     */
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * 当前正在执行或等待调用方关闭的 HTTP 调用。
     */
    private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
    /**
     * 当前仍可能接收事件的 SSE 会话。
     */
    private final Set<SseSession> activeSseSessions = ConcurrentHashMap.newKeySet();

    HttpClient(OkHttpClient okHttpClient, boolean ownsResources, int maxBufferedResponseBytes) {
        this(okHttpClient, ownsResources, maxBufferedResponseBytes, null,
                HttpHeaders.of(), JsonCodec.defaultCodec(), SseOptions.DEFAULT_IDLE_TIMEOUT);
    }

    HttpClient(OkHttpClient okHttpClient, boolean ownsResources, int maxBufferedResponseBytes,
               @Nullable URI baseUrl, HttpHeaders defaultHeaders, JsonCodec jsonCodec,
               Duration sseIdleTimeout) {
        this.okHttpClient = ValidationUtils.requireNonNull(okHttpClient, "okHttpClient must not be null");
        this.ownsResources = ownsResources;
        this.maxBufferedResponseBytes = requireBufferLimit(maxBufferedResponseBytes);
        this.baseUrl = baseUrl;
        this.defaultHeaders = ValidationUtils.requireNonNull(defaultHeaders,
                "defaultHeaders must not be null");
        this.jsonCodec = ValidationUtils.requireNonNull(jsonCodec,
                "jsonCodec must not be null");
        Duration idleTimeout = ValidationUtils.requireNonNull(sseIdleTimeout,
                "sseIdleTimeout must be non-negative");
        ValidationUtils.requireTrue(!idleTimeout.isNegative(),
                "sseIdleTimeout must be non-negative");
        this.sseIdleTimeout = idleTimeout;
    }

    /**
     * 创建客户端构建器。
     *
     * @return 新的客户端构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用默认调用选项执行请求并完整缓冲响应。
     *
     * @param request 待执行的请求
     * @return HTTP 响应，包含已缓冲的响应体
     */
    public HttpResponse send(HttpRequest request) {
        return send(request, RequestOptions.defaults());
    }

    /**
     * 使用新命名的单次请求选项发送缓冲请求。
     */
    public HttpResponse send(HttpRequest request, RequestOptions options) {
        RequestOptions effectiveOptions = ValidationUtils.requireNonNull(options, "options must not be null");
        return sendBuffered(request, effectiveOptions, effectiveOptions.maxBufferedResponseBytes());
    }

    /**
     * 使用客户端 JSON 编解码器编码请求体；传入的 body 会替换 request 原有请求体。
     */
    public HttpResponse sendJson(HttpRequest request, Object body) {
        return send(ValidationUtils.requireNonNull(request, "request must not be null")
                .withBody(HttpBodyUtils.json(body, jsonCodec)));
    }

    /**
     * 发送 JSON 并在成功时直接解码目标类型。
     */
    public <T> T sendJson(HttpRequest request, Object body, Class<T> responseType) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        ValidationUtils.requireNonNull(responseType, "responseType must not be null");
        return sendJson(request, body).requireSuccess()
                .bodyAs(responseType);
    }

    /**
     * 使用泛型类型信息发送并解码 JSON。
     */
    public <T> T sendJson(HttpRequest request, Object body, TypeReference<T> responseType) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        ValidationUtils.requireNonNull(responseType, "responseType must not be null");
        return sendJson(request, body).requireSuccess()
                .bodyAs(responseType);
    }

    /**
     * 执行请求并完整缓冲响应。即使服务端省略或谎报 Content-Length，仍会强制执行响应上限；
     * HTTP 4xx/5xx 响应作为正常响应返回。
     */
    private HttpResponse sendBuffered(HttpRequest request, RequestOptions options,
                                      @Nullable Integer maxResponseBytesOverride) {
        requireRequestAndOptions(request, options);
        ensureOpen();
        Call call = newCall(request, options);
        register(call);
        Instant requestTime = Instant.now();
        long startedNanos = System.nanoTime();

        // try-with-resources 覆盖所有返回路径，确保缓冲完成后连接可以复用。
        try (Response response = call.execute()) {
            long declaredLength = response.body().contentLength();
            int maximum = maxResponseBytesOverride == null
                    ? maxBufferedResponseBytes : maxResponseBytesOverride;
            if (declaredLength > maximum) {
                throw responseTooLarge(request, maximum);
            }
            byte[] body = readBounded(response.body(), maximum, request);
            ensureCallNotCancelled(call, request);
            HttpCallMetadata metadata = metadata(request, response, requestTime, startedNanos);
            return new HttpResponse(response.code(), response.message(),
                    HttpHeaders.fromOkHttp(response.headers()), body,
                    HttpResponse.responseCharset(response), metadata, response.protocol().toString(), jsonCodec);
        } catch (IOException exception) {
            throw transportFailure(request, exception, call.isCanceled());
        } finally {
            activeCalls.remove(call);
        }
    }

    /**
     * 执行请求但不缓冲响应；响应所有权转交给返回的 {@link HttpStream}。
     */
    private HttpStream stream(HttpRequest request, RequestOptions options) {
        requireRequestAndOptions(request, options);
        ensureOpen();
        Call call = newCall(request, options);
        register(call);
        Instant requestTime = Instant.now();
        long startedNanos = System.nanoTime();
        Response response = null;
        boolean handedOff = false;
        try {
            response = call.execute();
            ensureCallNotCancelled(call, request);
            // 关闭 HttpStream 时才移除活动调用
            HttpStream result = new HttpStream(
                    response, metadata(request, response, requestTime, startedNanos),
                    () -> activeCalls.remove(call));
            handedOff = true;
            return result;
        } catch (IOException exception) {
            closeQuietly(response);
            throw transportFailure(request, exception, call.isCanceled());
        } catch (RuntimeException exception) {
            closeQuietly(response);
            throw exception;
        } finally {
            if (!handedOff) {
                activeCalls.remove(call);
            }
        }
    }

    /**
     * 使用默认调用选项打开简洁的流式响应句柄。
     *
     * @param request 待执行的请求
     * @return 调用方负责关闭的流式响应句柄
     */
    public HttpStream openStream(HttpRequest request) {
        return stream(request, RequestOptions.defaults());
    }

    /**
     * 使用单次调用选项打开简洁的流式响应句柄。
     *
     * @param request 待执行的请求
     * @param options 本次调用的超时选项
     * @return 调用方负责关闭的流式响应句柄
     */
    public HttpStream openStream(HttpRequest request, RequestOptions options) {
        return stream(request, ValidationUtils.requireNonNull(options, "options must not be null"));
    }

    /**
     * 使用客户端默认空闲超时启动 SSE 会话。
     *
     * @param request  SSE 请求
     * @param listener 接收会话事件的监听器
     * @return 可用于取消的会话句柄
     */
    public SseSession openSse(HttpRequest request, SseListener listener) {
        return openSse(request, SseOptions.builder().idleTimeout(sseIdleTimeout).build(), listener);
    }

    /**
     * 启动 SSE 会话。SSE 不使用总调用超时，改由空闲超时限制两次事件之间的等待；
     * 请求配置错误在注册会话前同步抛出，不会触发终态回调。
     *
     * @param request  SSE 请求
     * @param options  SSE 选项
     * @param listener 接收会话事件的监听器
     * @return 可用于取消的会话句柄
     */
    public SseSession openSse(HttpRequest request, SseOptions options, SseListener listener) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        ValidationUtils.requireNonNull(options, "options must not be null");
        ValidationUtils.requireNonNull(listener, "listener must not be null");
        ensureOpen();

        HttpRequest effective = request;
        // 显式指定 Accept，避免服务端按普通 HTTP 响应协商；调用方自己的请求头优先
        if (!effective.headers().contains(HttpHeaderNames.ACCEPT)) {
            effective = effective.withHeader(HttpHeaderNames.ACCEPT, "text/event-stream");
        }
        if (options.lastEventId() != null) {
            effective = effective.withHeader(HttpHeaderNames.LAST_EVENT_ID, options.lastEventId());
        }
        Request transportRequest = effective.toOkHttpRequest(baseUrl, defaultHeaders);

        // 会话进入终态后自行从活动集合移除
        SseSession session = new SseSession(listener, activeSseSessions::remove);
        activeSseSessions.add(session);
        if (closed.get()) {
            session.cancel();
            throw new IllegalStateException("HTTP client is closed");
        }

        try {
            // SSE 请求默认不可重放，禁止 OkHttp 在底层静默重试；空闲超时由 readTimeout 实现
            OkHttpClient callClient = okHttpClient.newBuilder()
                    .retryOnConnectionFailure(false)
                    .readTimeout(options.idleTimeout())
                    .callTimeout(Duration.ZERO)
                    .build();
            EventSource source = EventSources.createFactory(callClient).newEventSource(
                    transportRequest, new EventSourceListener() {
                        @Override
                        public void onOpen(EventSource eventSource, Response response) {
                            session.bind(eventSource);
                            session.opened(response.code(), HttpHeaders.fromOkHttp(response.headers()));
                        }

                        @Override
                        public void onEvent(EventSource eventSource, @Nullable String id,
                                            @Nullable String type, String data) {
                            session.bind(eventSource);
                            session.event(new SseEvent(id, type, data));
                        }

                        @Override
                        public void onClosed(EventSource eventSource) {
                            session.bind(eventSource);
                            session.remoteClosed();
                        }

                        @Override
                        public void onFailure(EventSource eventSource, @Nullable Throwable throwable,
                                              @Nullable Response response) {
                            session.bind(eventSource);
                            Integer status = null;
                            HttpHeaders headers = null;
                            String body = null;
                            if (response != null) {
                                status = response.code();
                                headers = HttpHeaders.fromOkHttp(response.headers());
                                body = peekFailureBody(response);
                            }
                            HttpFailureKind kind = throwable == null
                                    ? HttpFailureKind.PROTOCOL : classify(throwable, false);
                            session.fail(new SseFailure(kind, throwable, status, headers, body));
                        }
                    });
            session.bind(source);
            return session;
        } catch (RuntimeException exception) {
            HttpFailureKind kind = classify(exception, false);
            session.fail(new SseFailure(kind, exception, null, null, null));
            throw new HttpException(kind, request.method().name(), requestUrl(request),
                    "could not start SSE session", exception);
        }
    }

    /**
     * 判断客户端是否已经关闭。
     *
     * @return 已关闭时为 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // 绝不能调用 dispatcher.cancelAll()：借入的客户端可能被无关使用方共享。
        for (SseSession session : List.copyOf(activeSseSessions)) {
            session.cancel();
        }
        for (Call call : List.copyOf(activeCalls)) {
            call.cancel();
        }

        if (ownsResources) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
            okhttp3.Cache cache = okHttpClient.cache();
            if (cache != null) {
                try {
                    cache.close();
                } catch (IOException ignored) {
                    // 调用和传输资源均已释放。
                }
            }
        }
    }

    OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    int maxBufferedResponseBytes() {
        return maxBufferedResponseBytes;
    }

    private Call newCall(HttpRequest request, RequestOptions options) {
        return clientFor(options).newCall(request.toOkHttpRequest(baseUrl, defaultHeaders));
    }

    private OkHttpClient clientFor(RequestOptions options) {
        if (options.isDefault()) {
            return okHttpClient;
        }
        // 每次覆盖都基于原客户端派生，连接池、调度器和拦截器仍保持一致
        OkHttpClient.Builder builder = okHttpClient.newBuilder();
        if (options.connectTimeout() != null) {
            builder.connectTimeout(options.connectTimeout());
        }
        if (options.readTimeout() != null) {
            builder.readTimeout(options.readTimeout());
        }
        if (options.writeTimeout() != null) {
            builder.writeTimeout(options.writeTimeout());
        }
        if (options.callTimeout() != null) {
            builder.callTimeout(options.callTimeout());
        }
        return builder.build();
    }

    private void register(Call call) {
        activeCalls.add(call);
        if (closed.get()) {
            activeCalls.remove(call);
            call.cancel();
            throw new IllegalStateException("HTTP client is closed");
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("HTTP client is closed");
        }
    }

    private void ensureCallNotCancelled(Call call, HttpRequest request) {
        if (call.isCanceled() || closed.get()) {
            throw new HttpException(HttpFailureKind.CANCELLED, request.method().name(),
                    requestUrl(request), "call was cancelled", null);
        }
    }

    private HttpException responseTooLarge(HttpRequest request, int maximum) {
        return new HttpException(HttpFailureKind.RESPONSE_TOO_LARGE,
                request.method().name(), requestUrl(request),
                "buffered response exceeds " + maximum + " bytes", null);
    }

    private String requestUrl(HttpRequest request) {
        return request.resolvedUrl(baseUrl);
    }

    private byte[] readBounded(ResponseBody body, int maximum, HttpRequest request)
            throws IOException {
        try (InputStream input = body.byteStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
            byte[] buffer = new byte[Math.min(maximum + 1, 8192)];
            while (true) {
                int remaining = maximum - output.size();
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining + 1));
                if (read < 0) {
                    return output.toByteArray();
                }
                if (read > remaining) {
                    throw new HttpException(HttpFailureKind.RESPONSE_TOO_LARGE,
                            request.method().name(), requestUrl(request),
                            "buffered response exceeds " + maximum + " bytes", null);
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private HttpCallMetadata metadata(HttpRequest request, Response response,
                                      Instant requestTime, long startedNanos) {
        Duration duration = Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
        return new HttpCallMetadata(
                IdUtils.nextUUIDString(),
                request.method().name(),
                requestUrl(request),
                requestTime,
                requestTime.plus(duration),
                duration,
                request.effectiveHeaders(defaultHeaders),
                HttpHeaders.fromOkHttp(response.headers()),
                response.protocol().toString(),
                response.code(),
                response.message());
    }

    private HttpException transportFailure(HttpRequest request, Throwable throwable,
                                           boolean cancelled) {
        return new HttpException(classify(throwable, cancelled), request.method().name(),
                requestUrl(request), "transport failure", throwable);
    }

    static HttpFailureKind classify(Throwable throwable, boolean cancelled) {
        // OkHttp 在配置的超时触发时会将调用标记为已取消。查询 isCanceled() 前应保留 TIMEOUT，
        // 同时显式取消仍优先于下方的套接字关闭噪声。
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException) {
                return HttpFailureKind.TIMEOUT;
            }
        }
        if (cancelled) {
            return HttpFailureKind.CANCELLED;
        }
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            switch (current) {
                case UnknownHostException unknownHostException -> {
                    return HttpFailureKind.DNS;
                }
                case SSLException sslException -> {
                    return HttpFailureKind.TLS;
                }
                case ProtocolException protocolException -> {
                    return HttpFailureKind.PROTOCOL;
                }
                case SocketException socketException -> {
                    return HttpFailureKind.CONNECTION;
                }
                default -> {
                }
            }
        }
        return HttpFailureKind.IO;
    }

    private static @Nullable String peekFailureBody(Response response) {
        try {
            return response.peekBody(MAX_SSE_FAILURE_BODY_BYTES).string();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void closeQuietly(@Nullable Response response) {
        if (response != null) {
            response.close();
        }
    }

    private static void requireRequestAndOptions(@Nullable HttpRequest request,
                                                 @Nullable RequestOptions options) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        ValidationUtils.requireNonNull(options, "options must not be null");
    }

    private static int requireBufferLimit(int value) {
        ValidationUtils.requireTrue(value >= 0 && value != Integer.MAX_VALUE,
                "maxBufferedResponseBytes must be between 0 and 2147483646");
        return value;
    }

    /**
     * 客户端级配置；单次调用的超时属于 {@link RequestOptions}。
     */
    public static final class Builder {
        public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
        public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
        public static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(10);
        public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(60);
        public static final int DEFAULT_MAX_IDLE_CONNECTIONS = 10;
        public static final Duration DEFAULT_KEEP_ALIVE_DURATION = Duration.ofMinutes(5);

        /**
         * 建立连接的默认超时时间；零表示不限制。
         */
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        /**
         * 读取响应数据的默认超时时间；零表示不限制。
         */
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        /**
         * 写出请求数据的默认超时时间；零表示不限制。
         */
        private Duration writeTimeout = DEFAULT_WRITE_TIMEOUT;
        /**
         * 单次 HTTP 调用的默认总超时时间；零表示不限制。
         */
        private Duration callTimeout = DEFAULT_CALL_TIMEOUT;
        /**
         * 连接池允许保留的最大空闲连接数。
         */
        private int maxIdleConnections = DEFAULT_MAX_IDLE_CONNECTIONS;
        /**
         * 空闲连接在连接池中的最长保留时间。
         */
        private Duration keepAliveDuration = DEFAULT_KEEP_ALIVE_DURATION;
        /**
         * 是否启用连接失败后的底层自动重试。
         */
        private boolean retryOnConnectionFailure;
        /**
         * 是否自动跟随同协议 HTTP 重定向。
         */
        private boolean followRedirects = true;
        /**
         * 是否允许跟随 HTTP 与 HTTPS 之间的重定向。
         */
        private boolean followSslRedirects;
        /**
         * 客户端级代理配置；未设置时直连。
         */
        private @Nullable HttpProxy proxy;
        /**
         * 缓冲响应正文允许的最大字节数。
         */
        private int maxBufferedResponseBytes = DEFAULT_MAX_BUFFERED_RESPONSE_BYTES;
        /**
         * 解析相对请求 URL 时使用的客户端基地址。
         */
        private @Nullable URI baseUrl;
        /**
         * 每个请求都会继承的默认请求头。
         */
        private HttpHeaders.Builder defaultHeaders = HttpHeaders.builder();
        /**
         * 客户端级 JSON 请求体和响应使用的编解码器。
         */
        private JsonCodec jsonCodec = JsonCodec.defaultCodec();
        /**
         * SSE 事件之间允许的默认空闲时间；零表示不限制。
         */
        private Duration sseIdleTimeout = SseOptions.DEFAULT_IDLE_TIMEOUT;
        /**
         * 在构建底层 OkHttp 客户端前执行的定制器。
         */
        private final List<Consumer<OkHttpClient.Builder>> okHttpCustomizers = new ArrayList<>();

        private Builder() {
        }

        /**
         * 设置建立连接的超时时间。
         *
         * @param timeout 非负的超时时间，零表示不超时
         * @return 当前构建器
         */
        public Builder connectTimeout(Duration timeout) {
            connectTimeout = requireTimeout(timeout, "connectTimeout");
            return this;
        }

        /**
         * 设置读取响应数据的超时时间。
         *
         * @param timeout 非负的超时时间，零表示不超时
         * @return 当前构建器
         */
        public Builder readTimeout(Duration timeout) {
            readTimeout = requireTimeout(timeout, "readTimeout");
            return this;
        }

        /**
         * 设置写出请求数据的超时时间。
         *
         * @param timeout 非负的超时时间，零表示不超时
         * @return 当前构建器
         */
        public Builder writeTimeout(Duration timeout) {
            writeTimeout = requireTimeout(timeout, "writeTimeout");
            return this;
        }

        /**
         * 设置整个 HTTP 调用的超时时间。
         *
         * @param timeout 非负的超时时间，零表示不超时
         * @return 当前构建器
         */
        public Builder callTimeout(Duration timeout) {
            callTimeout = requireTimeout(timeout, "callTimeout");
            return this;
        }

        /**
         * 设置是否自动跟随 HTTP 重定向。
         *
         * @param followRedirects 为 true 时自动跟随重定向
         * @return 当前构建器
         */
        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        /**
         * 设置是否允许跨协议 SSL 重定向。
         *
         * @param followSslRedirects 为 true 时允许跨协议重定向
         * @return 当前构建器
         */
        public Builder followSslRedirects(boolean followSslRedirects) {
            this.followSslRedirects = followSslRedirects;
            return this;
        }

        /**
         * 设置连接失败时是否由底层客户端自动重试。
         *
         * @param retryOnConnectionFailure 为 true 时启用自动重试
         * @return 当前构建器
         */
        public Builder retryOnConnectionFailure(boolean retryOnConnectionFailure) {
            this.retryOnConnectionFailure = retryOnConnectionFailure;
            return this;
        }

        /**
         * 设置连接池保留的空闲连接数和存活时间。
         *
         * @param maxIdleConnections 最大空闲连接数，不能为负数
         * @param keepAliveDuration  空闲连接存活时间，必须为正数
         * @return 当前构建器
         */
        public Builder connectionPool(int maxIdleConnections, Duration keepAliveDuration) {
            ValidationUtils.requireTrue(maxIdleConnections >= 0,
                    "maxIdleConnections must not be negative");
            Duration keepAlive = ValidationUtils.requireNonNull(keepAliveDuration,
                    "keepAliveDuration must be positive");
            ValidationUtils.requireTrue(!keepAlive.isZero() && !keepAlive.isNegative(),
                    "keepAliveDuration must be positive");
            this.maxIdleConnections = maxIdleConnections;
            this.keepAliveDuration = keepAlive;
            return this;
        }

        /**
         * 设置客户端级代理配置。
         *
         * @param proxy 代理配置
         * @return 当前构建器
         */
        public Builder proxy(HttpProxy proxy) {
            this.proxy = ValidationUtils.requireNonNull(proxy, "proxy must not be null");
            return this;
        }

        /**
         * 设置完整缓冲响应允许的最大字节数。
         *
         * @param maximum 最大字节数，范围为 0 至 2147483646
         * @return 当前构建器
         */
        public Builder maxBufferedResponseBytes(int maximum) {
            maxBufferedResponseBytes = requireBufferLimit(maximum);
            return this;
        }

        /**
         * 设置客户端默认基地址；请求可以继续使用绝对 URL。
         */
        public Builder baseUrl(String value) {
            ValidationUtils.requireNotBlank(value, "baseUrl must not be blank");
            try {
                return baseUrl(new URI(value));
            } catch (java.net.URISyntaxException exception) {
                throw new IllegalArgumentException("baseUrl must be a valid URI", exception);
            }
        }

        /**
         * 设置客户端默认基地址。
         */
        public Builder baseUrl(URI value) {
            ValidationUtils.requireNonNull(value, "baseUrl must not be null");
            if (!value.isAbsolute()
                    || !("http".equalsIgnoreCase(value.getScheme())
                    || "https".equalsIgnoreCase(value.getScheme()))) {
                throw new IllegalArgumentException("baseUrl must be an absolute HTTP or HTTPS URI");
            }
            if (value.getQuery() != null || value.getFragment() != null) {
                throw new IllegalArgumentException("baseUrl must not contain query or fragment");
            }
            String text = value.toString();
            baseUrl = URI.create(text.endsWith("/") ? text : text + "/");
            return this;
        }

        /**
         * 添加一个客户端默认 header。
         */
        public Builder defaultHeader(String name, String value) {
            defaultHeaders.set(name, value);
            return this;
        }

        /**
         * 替换客户端全部默认 header。
         */
        public Builder defaultHeaders(HttpHeaders headers) {
            ValidationUtils.requireNonNull(headers, "headers must not be null");
            defaultHeaders = HttpHeaders.builder();
            defaultHeaders.addAll(headers);
            return this;
        }

        public Builder bearerToken(String value) {
            return defaultHeader(HttpHeaderNames.AUTHORIZATION,
                    "Bearer " + ValidationUtils.requireNonNull(value, "token must not be null"));
        }

        /**
         * 设置 JSON 编解码器。
         */
        public Builder jsonCodec(JsonCodec value) {
            jsonCodec = ValidationUtils.requireNonNull(value, "jsonCodec must not be null");
            return this;
        }

        /**
         * 设置 SSE 的默认空闲超时；零表示不限制。
         */
        public Builder sseIdleTimeout(Duration value) {
            Duration timeout = ValidationUtils.requireNonNull(value,
                    "sseIdleTimeout must be non-negative");
            ValidationUtils.requireTrue(!timeout.isNegative(),
                    "sseIdleTimeout must be non-negative");
            sseIdleTimeout = timeout;
            return this;
        }

        Builder customizeOkHttp(Consumer<OkHttpClient.Builder> customizer) {
            ValidationUtils.requireNonNull(customizer, "customizer must not be null");
            okHttpCustomizers.add(customizer);
            return this;
        }

        /**
         * 创建拥有其底层传输资源的 HTTP 客户端。
         *
         * @return 新的 HTTP 客户端
         */
        public HttpClient build() {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectionPool(new ConnectionPool(maxIdleConnections,
                            keepAliveDuration.toMillis(), TimeUnit.MILLISECONDS))
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .writeTimeout(writeTimeout)
                    .callTimeout(callTimeout)
                    .retryOnConnectionFailure(retryOnConnectionFailure)
                    .followRedirects(followRedirects)
                    .followSslRedirects(followSslRedirects);
            if (proxy != null) {
                if (proxy.proxySelector() != null) {
                    builder.proxySelector(proxy.proxySelector());
                }
                if (proxy.authenticator() != null) {
                    builder.proxyAuthenticator(proxy.authenticator());
                }
            }
            for (Consumer<OkHttpClient.Builder> customizer : okHttpCustomizers) {
                customizer.accept(builder);
            }
            return new HttpClient(builder.build(), true, maxBufferedResponseBytes, baseUrl,
                    defaultHeaders.build(), jsonCodec, sseIdleTimeout);
        }

        private static Duration requireTimeout(@Nullable Duration value, String name) {
            Duration timeout = ValidationUtils.requireNonNull(value, name + " must be non-negative");
            ValidationUtils.requireTrue(!timeout.isNegative(), name + " must be non-negative");
            return timeout;
        }
    }
}
