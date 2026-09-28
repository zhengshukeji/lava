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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.jiandaoyun.data.BatchCreateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.BatchCreateResult;
import com.zhengshuyun.lava.jiandaoyun.data.BatchUpdateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.CreateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.DataCondition;
import com.zhengshuyun.lava.jiandaoyun.data.DataFilter;
import com.zhengshuyun.lava.jiandaoyun.data.DataRecord;
import com.zhengshuyun.lava.jiandaoyun.data.ListDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.UpdateDataRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简道云数据客户端的请求组装、value 包装、自动翻页和响应解析测试。
 */
class DataClientTest extends ClientTestSupport {
    /** 查询单条数据的响应正文。 */
    private static final String RECORD_RESPONSE = "{\"data\":{"
            + "\"_id\":\"d1\",\"appId\":\"" + APP_ID + "\",\"entryId\":\"" + ENTRY_ID + "\","
            + "\"creator\":{\"username\":\"xiaoyun\",\"name\":\"小云\",\"departments\":[1,3],\"type\":0,\"status\":1},"
            + "\"createTime\":\"2017-10-20T22:41:51.430Z\",\"updateTime\":\"2017-10-21T11:12:15.293Z\","
            + "\"_widget_1\":\"张三\",\"_widget_2\":18}}";

    /**
     * 验证查询单条数据的请求正文，以及系统字段与业务字段的解析。
     */
    @Test
    void getParsesSystemAndBusinessFields() {
        server.enqueue(200, RECORD_RESPONSE);

        DataRecord record = client.data().get(APP_ID, ENTRY_ID, "d1");

        assertRequest("/api/v5/app/entry/data/get",
                "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\",\"data_id\":\"d1\"}");
        assertEquals("d1", record.id());
        assertEquals(APP_ID, record.appId());
        assertEquals(ENTRY_ID, record.entryId());
        assertEquals("xiaoyun", record.creator().username());
        assertEquals(List.of(1L, 3L), record.creator().departments());
        assertNull(record.updater());
        assertEquals(Instant.parse("2017-10-20T22:41:51.430Z"), record.createTime());
        assertEquals("张三", record.value("_widget_1"));
        assertEquals(18, ((Number) record.value("_widget_2")).intValue());
        assertEquals("DataRecord[id=d1]", record.toString());
    }

    /**
     * 验证数据记录可以按字段注解转换为调用方 POJO。
     */
    @Test
    void recordConvertsToCallerPojo() {
        server.enqueue(200, RECORD_RESPONSE);

        Person person = client.data().get(APP_ID, ENTRY_ID, "d1").as(Person.class);

        assertEquals(new Person("d1", "张三", 18), person);
    }

    /**
     * 验证查询条件、字段、游标和过滤器按简道云结构序列化，未配置的参数省略。
     */
    @Test
    void listSendsFilterFieldsAndCursor() {
        server.enqueue(200, "{\"data\":[]}");

        client.data().list(ListDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID)
                .dataId("d0")
                .fields("_widget_1", "_widget_2")
                .limit(50)
                .filter(DataFilter.and(
                        DataCondition.eq("_widget_1", "张三"),
                        DataCondition.range("_widget_2", null, 20),
                        DataCondition.in("_widget_3", List.of("a", "b")),
                        DataCondition.empty("_widget_4")))
                .build());

        assertRequest("/api/v5/app/entry/data/list", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID
                + "\",\"data_id\":\"d0\",\"fields\":[\"_widget_1\",\"_widget_2\"],\"limit\":50,"
                + "\"filter\":{\"rel\":\"and\",\"cond\":["
                + "{\"field\":\"_widget_1\",\"method\":\"eq\",\"value\":[\"张三\"]},"
                + "{\"field\":\"_widget_2\",\"method\":\"range\",\"value\":[null,20]},"
                + "{\"field\":\"_widget_3\",\"method\":\"in\",\"value\":[\"a\",\"b\"]},"
                + "{\"field\":\"_widget_4\",\"method\":\"empty\"}]}}");
    }

    /**
     * 验证自动翻页以上一页最后一条数据的 ID 作为游标，直到某页不足一页为止。
     */
    @Test
    void listAllFollowsDataIdCursorUntilShortPage() {
        server.enqueue(200, "{\"data\":[{\"_id\":\"d1\"},{\"_id\":\"d2\"}]}");
        server.enqueue(200, "{\"data\":[{\"_id\":\"d3\"}]}");

        List<DataRecord> records = client.data().listAll(ListDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID).limit(2).build());

        assertEquals(List.of("d1", "d2", "d3"), records.stream().map(DataRecord::id).toList());
        assertRequest("/api/v5/app/entry/data/list",
                "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\",\"limit\":2}");
        assertRequest("/api/v5/app/entry/data/list",
                "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\",\"data_id\":\"d2\",\"limit\":2}");
    }

    /**
     * 验证写入值被包装为 value 结构：子表单行（含行 _id）逐字段包装，null 保留用于清空字段。
     */
    @Test
    void createWrapsValuesAndSubformRows() {
        server.enqueue(200, RECORD_RESPONSE);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", "row1");
        row.put("_widget_11", "王五");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_widget_1", "张三");
        data.put("_widget_2", List.of("选项1", "选项2"));
        data.put("_widget_3", Map.of("province", "江苏省"));
        data.put("_widget_4", List.of(row));
        data.put("_widget_5", null);

        DataRecord record = client.data().create(APP_ID, ENTRY_ID, data);

        assertEquals("d1", record.id());
        assertRequest("/api/v5/app/entry/data/create", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID
                + "\",\"data\":{"
                + "\"_widget_1\":{\"value\":\"张三\"},"
                + "\"_widget_2\":{\"value\":[\"选项1\",\"选项2\"]},"
                + "\"_widget_3\":{\"value\":{\"province\":\"江苏省\"}},"
                + "\"_widget_4\":{\"value\":[{\"_id\":{\"value\":\"row1\"},\"_widget_11\":{\"value\":\"王五\"}}]},"
                + "\"_widget_5\":{\"value\":null}}}");
    }

    /**
     * 验证新建请求的可选开关与提交人按简道云字段名写入。
     */
    @Test
    void createSendsOptionalFlags() {
        server.enqueue(200, RECORD_RESPONSE);

        client.data().create(CreateDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID)
                .data(Map.of("_widget_1", "张三"))
                .dataCreator("xiaoyun")
                .startWorkflow(true)
                .startTrigger(false)
                .transactionId("tx-1")
                .build());

        assertRequest("/api/v5/app/entry/data/create", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID
                + "\",\"data\":{\"_widget_1\":{\"value\":\"张三\"}},\"data_creator\":\"xiaoyun\","
                + "\"is_start_workflow\":true,\"is_start_trigger\":false,\"transaction_id\":\"tx-1\"}");
    }

    /**
     * 验证批量新建逐条包装数据并解析成功条数与 ID。
     */
    @Test
    void batchCreateWrapsEachRecordAndParsesResult() {
        server.enqueue(200, "{\"status\":\"success\",\"success_count\":2,\"success_ids\":[\"d1\",\"d2\"]}");

        BatchCreateResult result = client.data().batchCreate(BatchCreateDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID)
                .dataList(List.of(Map.of("_widget_1", "a"), Map.of("_widget_1", "b")))
                .build());

        assertEquals(new BatchCreateResult(2, List.of("d1", "d2")), result);
        assertRequest("/api/v5/app/entry/data/batch_create", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\""
                + ENTRY_ID + "\",\"data_list\":[{\"_widget_1\":{\"value\":\"a\"}},{\"_widget_1\":{\"value\":\"b\"}}]}");
    }

    /**
     * 验证修改单条与批量修改的请求正文。
     */
    @Test
    void updateAndBatchUpdateSendWrappedData() {
        server.enqueue(200, RECORD_RESPONSE);
        server.enqueue(200, "{\"status\":\"success\",\"success_count\":2}");

        client.data().update(UpdateDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID).dataId("d1")
                .data(Map.of("_widget_1", "李四")).startTrigger(true).build());
        int updated = client.data().batchUpdate(BatchUpdateDataRequest.builder()
                .appId(APP_ID).entryId(ENTRY_ID).dataIds(List.of("d1", "d2"))
                .data(Map.of("_widget_2", 100)).build());

        assertEquals(2, updated);
        assertRequest("/api/v5/app/entry/data/update", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID
                + "\",\"data_id\":\"d1\",\"data\":{\"_widget_1\":{\"value\":\"李四\"}},\"is_start_trigger\":true}");
        assertRequest("/api/v5/app/entry/data/batch_update", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\""
                + ENTRY_ID + "\",\"data_ids\":[\"d1\",\"d2\"],\"data\":{\"_widget_2\":{\"value\":100}}}");
    }

    /**
     * 验证删除默认省略智能助手开关，批量删除返回成功条数。
     */
    @Test
    void deleteAndBatchDelete() {
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, "{\"status\":\"success\",\"success_count\":3}");

        client.data().delete(APP_ID, ENTRY_ID, "d1");
        client.data().delete(APP_ID, ENTRY_ID, "d1", true);
        int deleted = client.data().batchDelete(APP_ID, ENTRY_ID, List.of("d1", "d2", "d3"));

        assertEquals(3, deleted);
        String prefix = "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\",";
        assertRequest("/api/v5/app/entry/data/delete", prefix + "\"data_id\":\"d1\"}");
        assertRequest("/api/v5/app/entry/data/delete", prefix + "\"data_id\":\"d1\",\"is_start_trigger\":true}");
        assertRequest("/api/v5/app/entry/data/batch_delete", prefix + "\"data_ids\":[\"d1\",\"d2\",\"d3\"]}");
    }

    /**
     * 验证缺少必填参数时在发请求前即被拒绝。
     */
    @Test
    void rejectsMissingRequiredArguments() {
        assertThrows(IllegalArgumentException.class, () -> client.data().get(APP_ID, ENTRY_ID, " "));
        assertThrows(IllegalArgumentException.class,
                () -> ListDataRequest.builder().appId(APP_ID).build());
        assertThrows(IllegalArgumentException.class,
                () -> CreateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).build());
        assertThrows(IllegalArgumentException.class,
                () -> UpdateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).data(Map.of()).build());
        Map<String, Object> blankField = new HashMap<>();
        blankField.put(" ", "x");
        assertThrows(IllegalArgumentException.class, () -> client.data().create(APP_ID, ENTRY_ID, blankField));
    }

    /**
     * 调用方自定义的表单数据 POJO。
     *
     * @param id 数据 ID
     * @param name 姓名字段
     * @param age 年龄字段
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(
            @JsonProperty("_id") String id,
            @JsonProperty("_widget_1") String name,
            @JsonProperty("_widget_2") int age
    ) {
    }
}
