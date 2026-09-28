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

import com.zhengshuyun.lava.jiandaoyun.application.Application;
import com.zhengshuyun.lava.jiandaoyun.application.ListApplicationsRequest;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunApiException;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import com.zhengshuyun.lava.json.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云根客户端的构建约束、鉴权注入和统一错误映射测试。
 */
class JiandaoyunClientTest {
    /**
     * 测试使用的固定 API Key。
     */
    private static final String API_KEY = "test-api-key";

    /**
     * 按请求顺序返回预设响应的本地模拟网关。
     */
    private JiandaoyunTestServer server;
    /**
     * 指向模拟网关的受测客户端。
     */
    private JiandaoyunClient client;

    /**
     * 启动独立模拟网关并构建受测客户端。
     */
    @BeforeEach
    void setUp() {
        server = JiandaoyunTestServer.start();
        client = clientBuilder().build();
    }

    /**
     * 关闭受测客户端和模拟网关。
     */
    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    /**
     * 构建指向模拟网关的客户端构建器。
     *
     * @return 已配置 API Key 与模拟网关根地址的构建器
     */
    private JiandaoyunClient.Builder clientBuilder() {
        return JiandaoyunClient.builder()
                .apiKey(API_KEY)
                .apiBaseUrl(server.baseUrl());
    }

    /**
     * 验证列表请求携带 Bearer 鉴权头，且未配置的分页字段在正文中被省略。
     */
    @Test
    void listApplicationsSendsBearerTokenAndOmitsAbsentPaging() {
        server.enqueue(200, "{\"apps\":[{\"name\":\"应用1\",\"app_id\":\"app-1\"}]}");

        List<Application> applications =
                client.applications().list(ListApplicationsRequest.builder().build());

        assertEquals(1, applications.size());
        assertEquals("应用1", applications.getFirst().name());
        assertEquals("app-1", applications.getFirst().appId());
        JiandaoyunTestServer.CapturedRequest request = server.takeRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/v5/app/list", request.target());
        assertEquals("Bearer " + API_KEY, request.header("Authorization"));
        assertEquals("lava-jiandaoyun", request.header("User-Agent"));
        assertEquals(0, JsonCodec.defaultCodec().readTree(
                new String(request.body(), StandardCharsets.UTF_8)).size());
    }

    /**
     * 验证简道云结构化错误响应转换为携带状态码、错误码和描述的领域异常。
     */
    @Test
    void apiErrorMapsToStructuredException() {
        server.enqueue(400, "{\"code\":8303,\"msg\":\"超出请求频率限制\"}");

        JiandaoyunApiException exception = assertThrows(JiandaoyunApiException.class,
                () -> client.applications().list(ListApplicationsRequest.builder().build()));

        assertEquals(400, exception.statusCode());
        assertEquals(8303, exception.code());
        assertEquals("超出请求频率限制", exception.apiMessage());
        assertTrue(exception.getMessage().contains("code=8303"));
    }

    /**
     * 验证无法解析出 code/msg 结构的错误正文按协议异常处理。
     */
    @Test
    void nonJsonErrorBodyThrowsProtocolException() {
        server.enqueue(502, "Bad Gateway");

        assertThrows(JiandaoyunProtocolException.class,
                () -> client.applications().list(ListApplicationsRequest.builder().build()));
    }

    /**
     * 验证成功状态但正文不是预期 JSON 结构时按协议异常处理。
     */
    @Test
    void malformedSuccessBodyThrowsProtocolException() {
        server.enqueue(200, "not-json");

        assertThrows(JiandaoyunProtocolException.class,
                () -> client.applications().list(ListApplicationsRequest.builder().build()));
    }

    /**
     * 验证关闭后的客户端拒绝所有领域入口调用。
     */
    @Test
    void closedClientRejectsDomainEntryPoints() {
        client.close();

        assertThrows(IllegalStateException.class, () -> client.applications());
        assertThrows(IllegalStateException.class, () -> client.forms());
        assertThrows(IllegalStateException.class, () -> client.data());
        assertThrows(IllegalStateException.class, () -> client.files());
        assertThrows(IllegalStateException.class, () -> client.workflows());
        assertThrows(IllegalStateException.class, () -> client.members());
        assertThrows(IllegalStateException.class, () -> client.departments());
        assertThrows(IllegalStateException.class, () -> client.roles());
        assertThrows(IllegalStateException.class, () -> client.roleGroups());
        assertThrows(IllegalStateException.class, () -> client.guests());
        assertThrows(IllegalStateException.class, () -> client.usage());
        assertThrows(IllegalStateException.class, () -> client.auditLogs());
    }

    /**
     * 验证错误正文使用 message 字段（而非 msg）时同样能解析出错误描述。
     */
    @Test
    void apiErrorAcceptsMessageField() {
        server.enqueue(400, "{\"code\":1010,\"message\":\"用户不存在\"}");

        JiandaoyunApiException exception = assertThrows(JiandaoyunApiException.class,
                () -> client.applications().list(ListApplicationsRequest.builder().build()));

        assertEquals(1010, exception.code());
        assertEquals("用户不存在", exception.apiMessage());
    }

    /**
     * 验证成功状态且 status=success 的正文不会被误判为业务失败。
     */
    @Test
    void successStatusBodyIsNotTreatedAsFailure() {
        server.enqueue(200, "{\"status\":\"success\",\"apps\":[]}");

        assertEquals(0, client.applications().list(ListApplicationsRequest.builder().build()).size());
    }

    /**
     * 验证未配置 API Key 时构建失败。
     */
    @Test
    void builderRequiresApiKey() {
        assertThrows(IllegalArgumentException.class,
                () -> JiandaoyunClient.builder().apiBaseUrl(server.baseUrl()).build());
    }

    /**
     * 验证同一构建器可以构建多个互相独立的客户端。
     */
    @Test
    void builderCanBuildIndependentClients() {
        JiandaoyunClient.Builder builder = clientBuilder();
        try (JiandaoyunClient first = builder.build(); JiandaoyunClient second = builder.build()) {
            assertNotSame(first, second);
            first.close();
            assertThrows(IllegalStateException.class, first::forms);
            assertNotNull(second.forms());
        }
    }

    /**
     * 验证 API Key 未配置或为空白时构建失败。
     */
    @Test
    void apiKeyRejectsMissingValue() {
        assertThrows(IllegalArgumentException.class,
                () -> JiandaoyunClient.builder().apiKey(" "));
        assertThrows(IllegalArgumentException.class,
                () -> JiandaoyunClient.builder().apiKey(""));
    }

    /**
     * 验证非环回主机不允许使用 HTTP 根地址，防止密钥明文出网。
     */
    @Test
    void apiBaseUrlRejectsNonLoopbackHttp() {
        assertThrows(IllegalArgumentException.class,
                () -> JiandaoyunClient.builder().apiKey(API_KEY).apiBaseUrl("http://example.com/"));
    }
}
