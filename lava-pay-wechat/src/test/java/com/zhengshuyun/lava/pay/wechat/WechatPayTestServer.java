/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhengshuyun.lava.crypto.CryptoUtils;
import com.zhengshuyun.lava.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 为微信支付客户端测试提供本地 APIv3 网关，按队列返回可签名响应并捕获实际请求。
 */
final class WechatPayTestServer implements AutoCloseable {
    /**
     * 模拟响应签名声明的固定微信支付公钥 ID。
     */
    static final String PUBLIC_KEY_ID = "PUB_KEY_ID_1234567890";
    /**
     * 模拟 APIv3 响应签名使用的固定随机串。
     */
    static final String RESPONSE_NONCE = "response-nonce";

    /**
     * 绑定到本机随机端口的 JDK HTTP 服务端。
     */
    private final HttpServer server;
    /**
     * 为每个模拟 HTTP 交换创建虚拟线程的执行器。
     */
    private final ExecutorService executor;
    /**
     * 默认用于生成模拟 APIv3 响应签名的 RSA 私钥。
     */
    private final PrivateKey responsePrivateKey;
    /**
     * 默认响应签名生成 Unix 秒级时间戳所用的时钟。
     */
    private final Clock clock;
    /**
     * 按请求到达顺序消费的预设响应队列；未预设时返回 500。
     */
    private final BlockingQueue<PlannedResponse> responses = new LinkedBlockingQueue<>();
    /**
     * 保存已接收请求的阻塞队列，供测试线程按到达顺序取出断言。
     */
    private final BlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();

    /**
     * 封装由该测试服务器管理生命周期的 HTTP 服务端、执行器、签名私钥和时钟。
     *
     * @param server 已绑定本地地址的 HTTP 服务端
     * @param executor 处理请求的虚拟线程执行器
     * @param responsePrivateKey 默认签署模拟响应的 RSA 私钥
     * @param clock 默认响应签名时间戳所用时钟
     */
    private WechatPayTestServer(
            HttpServer server,
            ExecutorService executor,
            PrivateKey responsePrivateKey,
            Clock clock
    ) {
        this.server = server;
        this.executor = executor;
        this.responsePrivateKey = responsePrivateKey;
        this.clock = clock;
    }

    /**
     * 在本机回环地址的随机端口启动微信支付模拟网关。
     *
     * @param responsePrivateKey 默认签署模拟 APIv3 响应的 RSA 私钥
     * @param clock 默认响应签名时间戳所用时钟
     * @return 已启动且可接收请求的模拟服务端
     * @throws AssertionError 当本地地址绑定或 HTTP 服务端创建失败时抛出
     */
    static WechatPayTestServer start(PrivateKey responsePrivateKey, Clock clock) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            WechatPayTestServer result = new WechatPayTestServer(
                    server,
                    executor,
                    responsePrivateKey,
                    clock
            );
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
     * 预设使用默认私钥、公钥 ID 和当前时钟签名的 JSON 响应。
     *
     * @param status 要返回的 HTTP 状态码
     * @param body UTF-8 JSON 响应正文
     */
    void enqueueSigned(int status, String body) {
        enqueueSigned(
                status,
                body.getBytes(StandardCharsets.UTF_8),
                PUBLIC_KEY_ID,
                responsePrivateKey,
                clock.instant().getEpochSecond()
        );
    }

    /**
     * 预设使用指定公钥 ID、私钥和时间戳签名的字节响应，用于构造安全边界。
     *
     * @param status 要返回的 HTTP 状态码
     * @param body 响应正文，入队时执行防御性复制
     * @param serial 写入 {@code Wechatpay-Serial} 头的公钥 ID
     * @param privateKey 签署该响应的 RSA 私钥
     * @param timestamp 签名使用的 Unix 秒级时间戳
     */
    void enqueueSigned(
            int status,
            byte[] body,
            String serial,
            PrivateKey privateKey,
            long timestamp
    ) {
        responses.add(new PlannedResponse(
                status,
                body.clone(),
                "application/json",
                true,
                serial,
                privateKey,
                timestamp
        ));
    }

    /**
     * 预设不包含 APIv3 签名头的响应，主要用于账单文件下载。
     *
     * @param status 要返回的 HTTP 状态码
     * @param body 响应正文，入队时执行防御性复制
     * @param contentType 写入响应头的媒体类型
     */
    void enqueueUnsigned(int status, byte[] body, String contentType) {
        responses.add(new PlannedResponse(
                status,
                body.clone(),
                contentType,
                false,
                "",
                responsePrivateKey,
                0
        ));
    }

    /**
     * 返回已捕获但尚未取出的请求数量，用于断言客户端在本地校验失败时未发出请求。
     *
     * @return 队列中的请求数量
     */
    int requestCount() {
        return requests.size();
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
     * 为指定响应正文生成完整的微信支付 APIv3 验签请求头。
     *
     * @param body 签名覆盖的原始响应正文
     * @param key 生成 RSA-SHA256 签名的微信支付私钥
     * @param serial 声明签名密钥的公钥 ID
     * @param timestamp 签名使用的 Unix 秒级时间戳
     * @return 包含公钥 ID、签名、时间戳、随机串和签名算法的 HTTP 头
     */
    static HttpHeaders signedHeaders(
            byte[] body,
            PrivateKey key,
            String serial,
            long timestamp
    ) {
        String signature = Base64.getEncoder().encodeToString(
                CryptoUtils.rsaSha256Sign(key,
                        responseMessage(timestamp, RESPONSE_NONCE, body)));
        return HttpHeaders.of(
                "Wechatpay-Serial",
                serial,
                "Wechatpay-Signature",
                signature,
                "Wechatpay-Timestamp",
                Long.toString(timestamp),
                "Wechatpay-Nonce",
                RESPONSE_NONCE,
                "Wechatpay-Signature-Type",
                "WECHATPAY2-SHA256-RSA2048"
        );
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
     * 捕获入站请求，按队列选取预设响应，并根据计划写入 APIv3 签名头和正文。
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
                    "text/plain",
                    false,
                    "",
                    responsePrivateKey,
                    0
            );
        }
        exchange.getResponseHeaders().set("Content-Type", response.contentType);
        exchange.getResponseHeaders().set("Request-ID", "request-id-001");
        if (response.signed) {
            HttpHeaders signatureHeaders = signedHeaders(
                    response.body,
                    response.privateKey,
                    response.serial,
                    response.timestamp
            );
            for (String name : signatureHeaders.names()) {
                exchange.getResponseHeaders().set(name, signatureHeaders.get(name));
            }
        }
        if (response.status == 204) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.sendResponseHeaders(response.status, response.body.length);
            exchange.getResponseBody().write(response.body);
        }
        exchange.close();
    }

    /**
     * 按 APIv3 规则组装时间戳、随机串和原始正文组成的响应签名消息。
     *
     * @param timestamp Unix 秒级时间戳
     * @param nonce 响应签名随机串
     * @param body 原始响应正文字节
     * @return 以换行分隔并以换行结尾的 UTF-8 签名消息
     */
    private static byte[] responseMessage(long timestamp, String nonce, byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(body.length + 64);
        output.writeBytes(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.writeBytes(nonce.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.writeBytes(body);
        output.write('\n');
        return output.toByteArray();
    }

    /**
     * 完整保留模拟网关收到的 HTTP 请求，供测试校验路由、请求头、正文和签名。
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
     * 描述模拟网关对下一次请求的响应计划，可切换签名、密钥 ID、媒体类型和时间戳。
     *
     * @param status HTTP 响应状态码
     * @param body 要原样写入的响应正文字节
     * @param contentType 响应正文的媒体类型
     * @param signed 是否生成并写入 APIv3 响应签名头
     * @param serial 签名头声明的公钥 ID；未签名响应使用空字符串
     * @param privateKey 签署响应的 RSA 私钥；未签名时不使用
     * @param timestamp 签名的 Unix 秒级时间戳；未签名响应为 0
     */
    private record PlannedResponse(
            int status,
            byte[] body,
            String contentType,
            boolean signed,
            String serial,
            PrivateKey privateKey,
            long timestamp
    ) {
    }
}
