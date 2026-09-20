/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
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

package com.zhengshuyun.lava.jiandaoyun;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 为简道云客户端测试提供本地开放 API 网关，按队列返回预设响应并捕获实际请求。
 */
final class JiandaoyunTestServer implements AutoCloseable {
    /**
     * 绑定到本机随机端口的 JDK HTTP 服务端。
     */
    private final HttpServer server;
    /**
     * 为每个模拟 HTTP 交换创建虚拟线程的执行器。
     */
    private final ExecutorService executor;
    /**
     * 按请求到达顺序消费的预设响应队列；未预设时返回 500。
     */
    private final BlockingQueue<PlannedResponse> responses = new LinkedBlockingQueue<>();
    /**
     * 保存已接收请求的阻塞队列，供测试线程按到达顺序取出断言。
     */
    private final BlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();

    /**
     * 封装由该测试服务器管理生命周期的 HTTP 服务端和执行器。
     *
     * @param server 已绑定本地地址的 HTTP 服务端
     * @param executor 处理请求的虚拟线程执行器
     */
    private JiandaoyunTestServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    /**
     * 在本机回环地址的随机端口启动简道云模拟网关。
     *
     * @return 已启动且可接收请求的模拟服务端
     * @throws AssertionError 当本地地址绑定或 HTTP 服务端创建失败时抛出
     */
    static JiandaoyunTestServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            JiandaoyunTestServer result = new JiandaoyunTestServer(server, executor);
            server.createContext("/", result::handle);
            server.setExecutor(executor);
            server.start();
            return result;
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * 返回模拟网关当前实际监听的 HTTP 根地址。
     *
     * @return 以斜杠结尾、指向本机随机端口的 URI
     */
    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + '/');
    }

    /**
     * 预设 JSON 响应。
     *
     * @param status 要返回的 HTTP 状态码
     * @param body UTF-8 JSON 响应正文
     */
    void enqueue(int status, String body) {
        responses.add(new PlannedResponse(
                status,
                body.getBytes(StandardCharsets.UTF_8),
                "application/json"
        ));
    }

    /**
     * 在最多 5 秒内取出下一个已捕获请求，避免客户端未发请求时无限阻塞。
     *
     * @return 最早到达且尚未取出的请求
     * @throws AssertionError 当 5 秒内未收到请求，或等待线程被中断时抛出
     */
    CapturedRequest takeRequest() {
        try {
            CapturedRequest request = requests.poll(5, TimeUnit.SECONDS);
            if (request == null) {
                throw new AssertionError("server did not receive a request");
            }
            return request;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    /**
     * 立即停止本地 HTTP 服务端并关闭虚拟线程执行器；仅在测试清理阶段调用。
     */
    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    /**
     * 捕获入站请求，按队列选取预设响应并写回。
     *
     * @param exchange JDK HTTP 服务端提供的当前请求交换
     * @throws IOException 当读取请求或写入响应失败时抛出
     */
    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                Map.copyOf(exchange.getRequestHeaders()),
                requestBody
        ));

        PlannedResponse response = responses.poll();
        if (response == null) {
            response = new PlannedResponse(
                    500,
                    "missing test response".getBytes(StandardCharsets.UTF_8),
                    "text/plain"
            );
        }
        exchange.getResponseHeaders().set("Content-Type", response.contentType);
        exchange.sendResponseHeaders(response.status, response.body.length);
        exchange.getResponseBody().write(response.body);
        exchange.close();
    }

    /**
     * 完整保留模拟网关收到的 HTTP 请求，供测试校验路由、请求头和正文。
     *
     * @param method HTTP 请求方法
     * @param target 原始请求目标，包含路径和查询字符串
     * @param headers 请求头的不可变快照，值列表可保留重复请求头
     * @param body 已全量读取的原始请求正文字节
     */
    record CapturedRequest(
            String method,
            String target,
            Map<String, List<String>> headers,
            byte[] body
    ) {
        /**
         * 不区分大小写查找指定请求头，重复头仅返回第一个值。
         *
         * @param name 待查找的 HTTP 请求头名称
         * @return 第一个请求头值；请求头缺失时返回 {@code null}
         */
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * 描述模拟网关对下一次请求的响应计划。
     *
     * @param status HTTP 响应状态码
     * @param body 要原样写入的响应正文字节
     * @param contentType 响应正文的媒体类型
     */
    private record PlannedResponse(int status, byte[] body, String contentType) {
    }
}
