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

import okhttp3.Response;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 未缓冲的流式响应句柄；调用方必须关闭它以释放底层连接，推荐使用 try-with-resources。
 */
public final class HttpStream implements AutoCloseable {

    private final Response response;
    private final HttpHeaders headers;
    private final HttpCallMetadata metadata;
    /**
     * 关闭时通知客户端注销活动调用。
     */
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean bodyClaimed = new AtomicBoolean();

    HttpStream(Response response, HttpCallMetadata metadata, Runnable onClose) {
        this.response = response;
        this.headers = HttpHeaders.fromOkHttp(response.headers());
        this.metadata = metadata;
        this.onClose = onClose;
    }

    /**
     * 返回 HTTP 响应状态码。
     *
     * @return 状态码
     */
    public int statusCode() {
        return response.code();
    }

    /**
     * 返回 HTTP 响应状态文本。
     *
     * @return 状态文本
     */
    public String statusMessage() {
        return response.message();
    }

    /**
     * 判断响应是否为 2xx。
     *
     * @return 2xx 时返回 true
     */
    public boolean isSuccessful() {
        return response.isSuccessful();
    }

    /**
     * 判断响应是否为重定向。
     *
     * @return 重定向状态码时返回 true
     */
    public boolean isRedirect() {
        return response.isRedirect();
    }

    /**
     * 返回全部响应头。
     *
     * @return 响应头
     */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * 返回指定名称的最后一个响应头值。
     *
     * @param name 响应头名称，不区分大小写
     * @return 响应头值；不存在时为 null
     */
    public @Nullable String header(String name) {
        return headers.get(name);
    }

    /**
     * 返回指定名称的全部响应头值。
     *
     * @param name 响应头名称，不区分大小写
     * @return 响应头值列表
     */
    public List<String> headers(String name) {
        return headers.values(name);
    }

    /**
     * 返回服务端声明的正文长度。
     *
     * @return 正文字节数；未知时为 -1
     */
    public long contentLength() {
        return response.body().contentLength();
    }

    /**
     * 返回按 Content-Type 解析的字符集，缺省为 UTF-8。
     *
     * @return 响应字符集
     */
    public Charset charset() {
        return HttpResponse.responseCharset(response);
    }

    /**
     * 返回协商的 HTTP 协议，例如 {@code http/1.1}。
     *
     * @return 协议名称
     */
    public String protocol() {
        return response.protocol().toString();
    }

    /**
     * 返回响应正文流；只能获取一次，流的生命周期由本句柄的 {@link #close()} 管理。
     *
     * @return 响应正文流
     * @throws IllegalStateException 句柄已关闭，或正文流已被获取
     */
    public InputStream body() {
        if (closed.get()) {
            throw new IllegalStateException("response is closed");
        }
        if (!bodyClaimed.compareAndSet(false, true)) {
            throw new IllegalStateException("response body stream has already been obtained");
        }
        return response.body().byteStream();
    }

    /**
     * 返回已脱敏的调用元数据。
     *
     * @return 调用元数据
     */
    public HttpCallMetadata metadata() {
        return metadata;
    }

    /**
     * 关闭响应并释放连接；可重复调用。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                response.close();
            } finally {
                // 无论响应关闭是否抛错，都必须注销活动调用
                onClose.run();
            }
        }
    }

    @Override
    public String toString() {
        return metadata.toString();
    }
}
