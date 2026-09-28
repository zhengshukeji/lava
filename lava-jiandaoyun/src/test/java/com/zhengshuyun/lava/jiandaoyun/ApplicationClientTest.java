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
import com.zhengshuyun.lava.json.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简道云应用查询客户端的请求组装、响应解析和分页校验测试。
 */
class ApplicationClientTest {
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
        client = JiandaoyunClient.builder()
                .apiKey("test-api-key")
                .apiBaseUrl(server.baseUrl())
                .build();
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
     * 验证应用列表响应解析为应用模型，并容忍简道云未来新增的未知字段。
     */
    @Test
    void listApplicationsParsesApplicationsAndIgnoresUnknownFields() {
        server.enqueue(200, "{\"apps\":["
                + "{\"name\":\"应用1\",\"app_id\":\"app-1\",\"futureField\":\"x\"},"
                + "{\"name\":\"应用2\",\"app_id\":\"app-2\"}]}");

        List<Application> applications = client.applications()
                .list(ListApplicationsRequest.builder().build());

        assertEquals(2, applications.size());
        assertEquals("应用1", applications.getFirst().name());
        assertEquals("app-1", applications.getFirst().appId());
        assertEquals("app-2", applications.get(1).appId());
    }

    /**
     * 验证已配置的分页字段按简道云字段名写入请求正文。
     */
    @Test
    void listApplicationsSendsConfiguredPagingFields() {
        server.enqueue(200, "{\"apps\":[]}");

        client.applications()
                .list(ListApplicationsRequest.builder().limit(50).skip(100).build());

        JiandaoyunTestServer.CapturedRequest request = server.takeRequest();
        String body = JsonCodec.defaultCodec().readTree(
                new String(request.body(), StandardCharsets.UTF_8)).toString();
        assertEquals("{\"limit\":50,\"skip\":100}", body);
    }

    /**
     * 验证分页参数越界在构建请求时即被拒绝。
     */
    @Test
    void listApplicationsRejectsOutOfRangePaging() {
        assertThrows(IllegalArgumentException.class,
                () -> ListApplicationsRequest.builder().limit(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> ListApplicationsRequest.builder().skip(-1).build());
    }
}
