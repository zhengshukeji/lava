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

import com.zhengshuyun.lava.jiandaoyun.data.BatchCreateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.BatchUpdateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.CreateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.ListDataRequest;
import com.zhengshuyun.lava.jiandaoyun.data.UpdateDataRequest;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunApiException;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 数据请求快照、分页异常和响应最小结构的回归测试。 */
class DataClientBoundaryTest extends ClientTestSupport {
    /** 复制嵌套集合时保留 null，并隔离调用方复用的列表、行和地址对象。 */
    @Test
    void 四种写入请求不受原始嵌套集合修改影响() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "杭州");
        address.put("detail", null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", "row1");
        row.put("_widget_name", "原值");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row));
        List<Object> selections = new ArrayList<>(Arrays.asList("选项1", null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_widget_address", address);
        data.put("_widget_rows", rows);
        data.put("_widget_choices", selections);
        data.put("_widget_clear", null);
        CreateDataRequest create = CreateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).data(data).build();
        UpdateDataRequest update = UpdateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                .dataId("d1").data(data).build();
        BatchCreateDataRequest batchCreate = BatchCreateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                .dataList(List.of(data)).build();
        // 批量更新不支持子表单，使用地址和多选字段验证同一复制边界。
        Map<String, Object> batchData = new LinkedHashMap<>(data);
        batchData.remove("_widget_rows");
        BatchUpdateDataRequest batchUpdate = BatchUpdateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                .dataIds(List.of("d1")).data(batchData).build();

        address.clear();
        row.put("_widget_name", null);
        rows.clear();
        selections.clear();
        data.clear();
        batchData.clear();

        server.enqueue(200, "{\"data\":{}}");
        server.enqueue(200, "{\"data\":{}}");
        server.enqueue(200, "{\"status\":\"success\",\"success_count\":1,\"success_ids\":[\"d1\"]}");
        server.enqueue(200, "{\"status\":\"success\",\"success_count\":1}");
        client.data().create(create);
        client.data().update(update);
        client.data().batchCreate(batchCreate);
        client.data().batchUpdate(batchUpdate);

        String prefix = "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\"" + ENTRY_ID + "\",";
        String commonFields = "\"_widget_address\":{\"value\":{\"city\":\"杭州\",\"detail\":null}},"
                + "\"_widget_choices\":{\"value\":[\"选项1\",null]},\"_widget_clear\":{\"value\":null}";
        String wrapped = "{" + commonFields + ",\"_widget_rows\":{\"value\":[{"
                + "\"_id\":{\"value\":\"row1\"},\"_widget_name\":{\"value\":\"原值\"}}]}}";
        assertRequest("/api/v5/app/entry/data/create", prefix + "\"data\":" + wrapped + "}");
        assertRequest("/api/v5/app/entry/data/update", prefix + "\"data_id\":\"d1\",\"data\":" + wrapped + "}");
        assertRequest("/api/v5/app/entry/data/batch_create", prefix + "\"data_list\":[" + wrapped + "]}");
        assertRequest("/api/v5/app/entry/data/batch_update", prefix
                + "\"data_ids\":[\"d1\"],\"data\":{" + commonFields + "}}");

        for (Map<String, ?> snapshot : List.of(create.data(), update.data(), batchCreate.dataList().getFirst(),
                batchUpdate.data())) {
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            assertThrows(UnsupportedOperationException.class, ((Map<?, ?>) snapshot.get("_widget_address"))::clear);
            assertThrows(UnsupportedOperationException.class, ((List<?>) snapshot.get("_widget_choices"))::clear);
        }
        List<?> copiedRows = (List<?>) update.data().get("_widget_rows");
        assertThrows(UnsupportedOperationException.class, copiedRows::clear);
        assertThrows(UnsupportedOperationException.class, ((Map<?, ?>) copiedRows.getFirst())::clear);
    }

    /** 自动分页依赖 ID 去重，短页和满页都不能接受无效 ID。 */
    @Test
    void 分页记录缺少有效标识时抛协议异常() {
        for (String record : List.of("{}", "{\"_id\":null}", "{\"_id\":\" \"}", "{\"_id\":7}")) {
            for (int limit : List.of(1, 2)) {
                server.enqueue(200, "{\"data\":[" + record + "]}");
                assertThrows(JiandaoyunProtocolException.class, () -> client.data().listAll(
                        ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).limit(limit).build()), record);
            }
        }
    }

    /** 最后一个短页与上一页重叠时，保留首次查询到的记录。 */
    @Test
    void 短页重复记录按标识去重并保留首次值() {
        server.enqueue(200, "{\"data\":[{\"_id\":\"d1\"},{\"_id\":\"d2\",\"name\":\"首次\"}]}");
        server.enqueue(200, "{\"data\":[{\"_id\":\"d2\",\"name\":\"重复\"}]}");

        var records = client.data().listAll(ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).limit(2).build());

        assertEquals(List.of("d1", "d2"), records.stream().map(record -> record.id()).toList());
        assertEquals("首次", records.getLast().value("name"));
    }

    /** 去重后的条数不足一页也不能提前结束，仍需读取下一页。 */
    @Test
    void 满页重叠去重后继续按原始页条数翻页() {
        server.enqueue(200, "{\"data\":[{\"_id\":\"d1\"},{\"_id\":\"d2\"}]}");
        server.enqueue(200, "{\"data\":[{\"_id\":\"d2\"},{\"_id\":\"d3\"}]}");
        server.enqueue(200, "{\"data\":[{\"_id\":\"d4\"}]}");

        var records = client.data().listAll(ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).limit(2).build());

        assertEquals(List.of("d1", "d2", "d3", "d4"), records.stream().map(record -> record.id()).toList());
    }

    /** 既检查请求起始游标，也检查后续页游标，异常时不得继续发请求。 */
    @Test
    void 重复或倒退的分页游标被拒绝() {
        for (String nextId : List.of("d2", "d1")) {
            server.enqueue(200, "{\"data\":[{\"_id\":\"" + nextId + "\"}]}");
            assertThrows(JiandaoyunProtocolException.class, () -> client.data().listAll(
                    ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).dataId("d2").limit(1).build()));
        }
        server.enqueue(200, "{\"data\":[{\"_id\":\"d2\"}]}");
        server.enqueue(200, "{\"data\":[{\"_id\":\"d2\"}]}");
        assertThrows(JiandaoyunProtocolException.class, () -> client.data().listAll(
                ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).limit(1).build()));
    }

    /** 整页后返回空页合法，且每页继续携带原查询条件。 */
    @Test
    void 整页之后空页正常结束查询() {
        server.enqueue(200, "{\"data\":[{\"_id\":\"d1\"}]}");
        server.enqueue(200, "{\"data\":[]}");
        var records = client.data().listAll(ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                .fields("_widget_name").limit(1).build());
        assertEquals(1, records.size());
        assertRequest("/api/v5/app/entry/data/list", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\""
                + ENTRY_ID + "\",\"fields\":[\"_widget_name\"],\"limit\":1}");
        assertRequest("/api/v5/app/entry/data/list", "{\"app_id\":\"" + APP_ID + "\",\"entry_id\":\""
                + ENTRY_ID + "\",\"fields\":[\"_widget_name\"],\"limit\":1,\"data_id\":\"d1\"}");
    }

    /** 查询、新增和修改共用记录解析，拒绝缺失、null 和错误类型的 data。 */
    @Test
    void 单条接口拒绝缺失或错误类型的数据对象() {
        for (String body : List.of("", "null", "{}", "{\"data\":null}", "{\"data\":[]}",
                "{\"data\":17}", "{\"data\":\"错误\"}")) {
            for (Runnable action : List.<Runnable>of(
                    () -> client.data().get(APP_ID, ENTRY_ID, "d1"),
                    () -> client.data().create(APP_ID, ENTRY_ID, Map.of("_widget_1", "值")),
                    () -> client.data().update(UpdateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                            .dataId("d1").data(Map.of("_widget_1", "值")).build()))) {
                server.enqueue(200, body);
                assertThrows(JiandaoyunProtocolException.class, action::run, body);
            }
        }
    }

    /** 列表必须是对象数组，空数组仍是合法的查询结果。 */
    @Test
    void 列表接口拒绝无效数组或无效记录() {
        for (String body : List.of("{}", "null", "{\"data\":null}", "{\"data\":{}}",
                "{\"data\":[null]}", "{\"data\":[1]}", "{\"data\":[\"错误\"]}")) {
            server.enqueue(200, body);
            assertThrows(JiandaoyunProtocolException.class, () -> client.data().list(
                    ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).build()), body);
        }
        server.enqueue(200, "{\"data\":[],\"future_field\":true}");
        assertEquals(List.of(), client.data().list(ListDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID).build()));
    }

    /** 删除只认明确成功状态，不能把 HTTP 成功状态当成业务成功。 */
    @Test
    void 删除拒绝缺失或非成功状态() {
        for (String body : List.of("", "null", "{}", "{\"status\":null}", "{\"status\":\"pending\"}",
                "{\"status\":[]}")) {
            server.enqueue(200, body);
            assertThrows(JiandaoyunProtocolException.class, () -> client.data().delete(APP_ID, ENTRY_ID, "d1"), body);
        }
    }

    /** 三种批量接口均需成功状态和可用整数，不允许默认零或截断小数。 */
    @Test
    void 批量接口拒绝缺失状态或无效计数() {
        for (String body : List.of("{}", "{\"success_count\":0}",
                "{\"status\":\"pending\",\"success_count\":0}", "{\"status\":\"success\"}",
                "{\"status\":\"success\",\"success_count\":null}",
                "{\"status\":\"success\",\"success_count\":\"0\"}",
                "{\"status\":\"success\",\"success_count\":1.5}",
                "{\"status\":\"success\",\"success_count\":2147483648}")) {
            for (Runnable action : batchActions()) {
                server.enqueue(200, body);
                assertThrows(JiandaoyunProtocolException.class, action::run, body);
            }
        }
    }

    /** 合法零计数和部分成功不应被误判失败，未知响应字段保持兼容。 */
    @Test
    void 批量接口接受零计数及部分成功() {
        for (int count : List.of(0, 1)) {
            String body = "{\"status\":\"success\",\"success_count\":" + count
                    + ",\"success_ids\":[],\"future_field\":true}";
            server.enqueue(200, body);
            assertEquals(count, client.data().batchCreate(BatchCreateDataRequest.builder().appId(APP_ID)
                    .entryId(ENTRY_ID).dataList(List.of(Map.of("_widget_1", "a"), Map.of("_widget_1", "b")))
                    .build()).successCount());
            server.enqueue(200, body);
            assertEquals(count, client.data().batchUpdate(BatchUpdateDataRequest.builder().appId(APP_ID)
                    .entryId(ENTRY_ID).dataIds(List.of("d1", "d2")).data(Map.of("_widget_1", "a")).build()));
            server.enqueue(200, body);
            assertEquals(count, client.data().batchDelete(APP_ID, ENTRY_ID, List.of("d1", "d2")));
        }
    }

    /** 批量新增要提供 ID 数组，其他批量接口无需此字段。 */
    @Test
    void 批量新增拒绝缺失或无效成功标识数组() {
        for (String suffix : List.of("", ",\"success_ids\":null", ",\"success_ids\":{}", ",\"success_ids\":[null]")) {
            server.enqueue(200, "{\"status\":\"success\",\"success_count\":0" + suffix + "}");
            assertThrows(JiandaoyunProtocolException.class, batchActions().getFirst()::run);
        }
    }

    /** 业务失败仍由共享传输层映射，不能被新增的结构检查掩盖。 */
    @Test
    void 业务失败仍保留原始错误码() {
        server.enqueue(200, "{\"status\":\"failure\",\"code\":4008,\"message\":\"操作失败\"}");
        JiandaoyunApiException error = assertThrows(JiandaoyunApiException.class,
                () -> client.data().delete(APP_ID, ENTRY_ID, "d1"));
        assertEquals(4008, error.code());
    }

    /** 只约束返回结构，不校验表单字段值或要求普通查询包含数据 ID。 */
    @Test
    void 允许空业务字段及未知字段() {
        server.enqueue(200, "{\"data\":{\"amount\":null,\"future_field\":[1]},\"extra\":true}");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("amount", null);
        expected.put("future_field", List.of(1));
        assertEquals(expected, client.data().get(APP_ID, ENTRY_ID, "d1").values());
    }

    /**
     * 创建三种批量操作，复用同一组协议异常场景。
     *
     * @return 批量新增、修改和删除调用
     */
    private List<Runnable> batchActions() {
        return List.of(
                () -> client.data().batchCreate(BatchCreateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                        .dataList(List.of(Map.of("_widget_1", "值"))).build()),
                () -> client.data().batchUpdate(BatchUpdateDataRequest.builder().appId(APP_ID).entryId(ENTRY_ID)
                        .dataIds(List.of("d1")).data(Map.of("_widget_1", "值")).build()),
                () -> client.data().batchDelete(APP_ID, ENTRY_ID, List.of("d1")));
    }
}
