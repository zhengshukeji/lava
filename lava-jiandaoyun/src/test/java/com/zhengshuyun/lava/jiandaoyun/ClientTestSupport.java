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

import com.zhengshuyun.lava.json.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 领域客户端测试的公共夹具：每个用例独立的模拟网关与受测客户端，以及请求断言辅助方法。
 */
abstract class ClientTestSupport {
    /** 测试使用的固定应用 ID。 */
    static final String APP_ID = "5e0dca0cc9a2790006c11e02";
    /** 测试使用的固定表单 ID。 */
    static final String ENTRY_ID = "56fcab0f02c4675e3fe9694a";

    /** 按请求顺序返回预设响应的本地模拟网关。 */
    JiandaoyunTestServer server;
    /** 指向模拟网关的受测客户端。 */
    JiandaoyunClient client;

    /**
     * 启动独立模拟网关并构建受测客户端。
     */
    @BeforeEach
    void startServer() {
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
    void stopServer() {
        client.close();
        server.close();
    }

    /**
     * 取出下一个请求并断言路径与 JSON 正文；JSON 按树模型比较，与字段顺序无关。
     *
     * @param path 期望的请求路径
     * @param expectedJson 期望的 JSON 正文
     * @return 已捕获的请求
     */
    JiandaoyunTestServer.CapturedRequest assertRequest(String path, String expectedJson) {
        JiandaoyunTestServer.CapturedRequest request = server.takeRequest();
        assertEquals("POST", request.method());
        assertEquals(path, request.target());
        JsonCodec codec = JsonCodec.defaultCodec();
        assertEquals(codec.readTree(expectedJson), codec.readTree(request.bodyText()));
        return request;
    }
}
