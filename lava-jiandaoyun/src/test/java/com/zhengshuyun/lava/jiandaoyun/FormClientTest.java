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

import com.zhengshuyun.lava.jiandaoyun.form.Form;
import com.zhengshuyun.lava.jiandaoyun.form.FormWidgets;
import com.zhengshuyun.lava.jiandaoyun.form.ListFormsRequest;
import com.zhengshuyun.lava.json.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简道云表单查询客户端的请求组装、响应解析和参数校验测试。
 */
class FormClientTest {
    /**
     * 测试使用的固定应用 ID。
     */
    private static final String APP_ID = "5e0dca0cc9a2790006c11e02";
    /**
     * 测试使用的固定表单 ID。
     */
    private static final String ENTRY_ID = "56fcab0f02c4675e3fe9694a";

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
     * 验证表单列表响应解析为表单模型。
     */
    @Test
    void listFormsParsesForms() {
        server.enqueue(200, "{\"forms\":["
                + "{\"name\":\"表单1\",\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\"}]}");

        List<Form> forms = client.forms().list(
                ListFormsRequest.builder().appId(APP_ID).build());

        assertEquals(1, forms.size());
        assertEquals("表单1", forms.getFirst().name());
        assertEquals(APP_ID, forms.getFirst().appId());
        assertEquals(ENTRY_ID, forms.getFirst().entryId());
    }

    /**
     * 验证表单列表请求携带应用 ID 与分页字段。
     */
    @Test
    void listFormsSendsAppIdAndPagingFields() {
        server.enqueue(200, "{\"forms\":[]}");

        client.forms().list(ListFormsRequest.builder()
                .appId(APP_ID).limit(20).skip(40).build());

        JiandaoyunTestServer.CapturedRequest request = server.takeRequest();
        assertEquals("/api/v5/app/entry/list", request.target());
        String body = JsonCodec.defaultCodec().readTree(
                new String(request.body(), StandardCharsets.UTF_8)).toString();
        assertEquals("{\"app_id\":\"" + APP_ID + "\",\"limit\":20,\"skip\":40}", body);
    }

    /**
     * 验证未配置应用 ID 或分页越界时构建请求即被拒绝。
     */
    @Test
    void listFormsRejectsMissingAppIdAndOutOfRangePaging() {
        assertThrows(IllegalArgumentException.class, () -> ListFormsRequest.builder().build());
        assertThrows(IllegalArgumentException.class, () -> ListFormsRequest.builder().appId(" ").build());
        assertThrows(IllegalArgumentException.class,
                () -> ListFormsRequest.builder().appId(APP_ID).limit(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> ListFormsRequest.builder().appId(APP_ID).skip(-1).build());
    }

    /**
     * 验证表单字段响应完整解析业务字段、子表单递归结构、系统字段和修改时间。
     */
    @Test
    void listWidgetsParsesWidgetsSubformAndSysWidgets() {
        server.enqueue(200, "{\"widgets\":["
                + "{\"label\":\"单行文本\",\"name\":\"_widget_1\",\"widgetName\":\"_widget_1\",\"type\":\"text\"},"
                + "{\"label\":\"子表单\",\"name\":\"_widget_2\",\"widgetName\":\"_widget_2\",\"type\":\"subform\","
                + "\"items\":[{\"label\":\"子字段\",\"name\":\"child_1\",\"widgetName\":\"_widget_21\",\"type\":\"number\"}]}],"
                + "\"sysWidgets\":[{\"name\":\"flowState\"},{\"name\":\"createTime\"}],"
                + "\"dataModifyTime\":\"2021-09-08T03:40:26.586Z\"}");

        FormWidgets widgets = client.forms().listWidgets(APP_ID, ENTRY_ID);

        assertEquals(2, widgets.widgets().size());
        FormWidgets.Widget text = widgets.widgets().getFirst();
        assertEquals("单行文本", text.label());
        assertEquals("_widget_1", text.name());
        assertEquals("text", text.type());
        assertNull(text.items());
        FormWidgets.Widget subform = widgets.widgets().get(1);
        assertEquals("subform", subform.type());
        assertEquals(1, subform.items().size());
        assertEquals("child_1", subform.items().getFirst().name());
        assertEquals(2, widgets.sysWidgets().size());
        assertEquals("flowState", widgets.sysWidgets().getFirst().name());
        assertEquals(Instant.parse("2021-09-08T03:40:26.586Z"), widgets.dataModifyTime());
    }

    /**
     * 验证表单字段请求正文使用简道云要求的 snake_case 字段名。
     */
    @Test
    void listWidgetsSendsSnakeCaseIdentifiers() {
        server.enqueue(200, "{\"widgets\":[],\"sysWidgets\":[],\"dataModifyTime\":\"2021-09-08T03:40:26.586Z\"}");

        client.forms().listWidgets(APP_ID, ENTRY_ID);

        JiandaoyunTestServer.CapturedRequest request = server.takeRequest();
        assertEquals("/api/v5/app/entry/widget/list", request.target());
        String body = JsonCodec.defaultCodec().readTree(
                new String(request.body(), StandardCharsets.UTF_8)).toString();
        assertEquals("{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\"}", body);
    }

    /**
     * 验证空白应用 ID 或表单 ID 在发请求前即被拒绝。
     */
    @Test
    void listWidgetsRejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> client.forms().listWidgets(" ", ENTRY_ID));
        assertThrows(IllegalArgumentException.class,
                () -> client.forms().listWidgets(APP_ID, " "));
    }
}
