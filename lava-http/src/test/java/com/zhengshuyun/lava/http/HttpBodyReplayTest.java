/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实 HTTP 往返验证一次性请求体与重定向、服务端重试的组合行为。
 */
class HttpBodyReplayTest {
    /** 用于比较首次上传与可能发生的重放内容。 */
    private static final byte[] PAYLOAD = "upload-payload".getBytes(StandardCharsets.UTF_8);

    /**
     * 已知和未知长度的输入流遇到 307/308 时均保留原响应，且不会关闭调用方的流。
     *
     * @throws Exception 本地测试服务启动或关闭失败时抛出
     */
    @Test
    void oneShotStreamsDoNotFollowBodyPreservingRedirects() throws Exception {
        for (int status : new int[]{307, 308}) {
            for (long length : new long[]{-1, PAYLOAD.length}) {
                try (ReplayServer server = new ReplayServer(status);
                     HttpClient client = HttpClient.builder().build();
                     BorrowedInputStream input = new BorrowedInputStream()) {
                    HttpResponse response = client.send(HttpRequest.post(server.url())
                            .body(HttpBodyUtils.stream(input, length, HttpMediaTypes.APPLICATION_OCTET_STREAM))
                            .build());

                    assertEquals(status, response.statusCode());
                    assertEquals("/target", response.location());
                    assertEquals(List.of("upload-payload"), server.bodies);
                    assertFalse(input.closed, "请求完成后输入流仍归调用方所有");
                }
            }
        }
    }

    /**
     * 可重放的字节请求体仍可跟随 307/308，目标端点必须收到完整正文。
     *
     * @throws Exception 本地测试服务启动或关闭失败时抛出
     */
    @Test
    void repeatableBytesFollowBodyPreservingRedirectsWithoutLosingContent() throws Exception {
        for (int status : new int[]{307, 308}) {
            try (ReplayServer server = new ReplayServer(status);
                 HttpClient client = HttpClient.builder().build()) {
                HttpResponse response = client.send(HttpRequest.post(server.url())
                        .body(HttpBodyUtils.bytes(PAYLOAD, HttpMediaTypes.APPLICATION_OCTET_STREAM))
                        .build());

                assertEquals(200, response.statusCode());
                assertEquals(List.of("upload-payload", "upload-payload"), server.bodies);
            }
        }
    }

    /**
     * 即使调用方开启连接失败重试，一次性流遇到可重试的 408/503 也不能再次上传。
     *
     * @throws Exception 本地测试服务启动或关闭失败时抛出
     */
    @Test
    void oneShotStreamsDoNotRetryAfterServerFailure() throws Exception {
        for (int status : new int[]{408, 503}) {
            try (ReplayServer server = new ReplayServer(status);
                 HttpClient client = HttpClient.builder().retryOnConnectionFailure(true).build();
                 BorrowedInputStream input = new BorrowedInputStream()) {
                HttpResponse response = client.send(HttpRequest.post(server.url())
                        .body(HttpBodyUtils.stream(input, -1, HttpMediaTypes.APPLICATION_OCTET_STREAM))
                        .build());

                assertEquals(status, response.statusCode());
                assertEquals(List.of("upload-payload"), server.bodies);
                assertFalse(input.closed);
            }
        }
    }

    /** 记录输入流是否被传输层提前关闭。 */
    private static final class BorrowedInputStream extends ByteArrayInputStream {
        /** 是否已经收到关闭调用。 */
        private boolean closed;

        /** 创建包含固定测试正文的输入流。 */
        private BorrowedInputStream() {
            super(PAYLOAD);
        }

        /** 记录关闭状态，字节数组输入流没有其他待释放资源。 */
        @Override
        public void close() {
            closed = true;
        }
    }

    /** 首次返回指定状态，后续请求返回成功，并记录每次实际收到的正文。 */
    private static final class ReplayServer implements AutoCloseable {
        /** 只监听环回地址的 HTTP 服务。 */
        private final HttpServer server;
        /** 按到达顺序记录正文，供客户端线程在响应完成后断言。 */
        private final List<String> bodies = new CopyOnWriteArrayList<>();

        /**
         * 启动可重定向或要求立即重试的测试端点。
         *
         * @param firstStatus 第一次请求使用的状态码
         * @throws IOException 无法绑定环回端口时抛出
         */
        private ReplayServer(int firstStatus) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    if (bodies.size() == 1) {
                        exchange.getResponseHeaders().set("Location", "/target");
                        exchange.getResponseHeaders().set("Retry-After", "0");
                        exchange.sendResponseHeaders(firstStatus, -1);
                    } else {
                        exchange.sendResponseHeaders(200, -1);
                    }
                }
            });
            server.start();
        }

        /**
         * 返回实际监听的上传地址。
         *
         * @return 环回上传 URL
         */
        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
        }

        /** 停止 HTTP 服务并释放监听端口。 */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
