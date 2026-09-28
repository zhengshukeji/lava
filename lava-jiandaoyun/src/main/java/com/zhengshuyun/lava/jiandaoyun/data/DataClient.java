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

package com.zhengshuyun.lava.jiandaoyun.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunDataUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简道云表单数据客户端，提供单条与批量的增删改查。
 *
 * <p>写入时直接传 {@code 字段名 -> 值}，SDK 会自动包装为简道云要求的 {@code {"value": 值}}：</p>
 * <pre>{@code
 * DataRecord record = client.data().create(appId, entryId, Map.of(
 *         "_widget_1", "张三",
 *         "_widget_2", 18));
 * }</pre>
 */
public final class DataClient {
    /** 查询单条数据接口路径。 */
    private static final String GET_PATH = "/api/v5/app/entry/data/get";
    /** 查询多条数据接口路径。 */
    private static final String LIST_PATH = "/api/v5/app/entry/data/list";
    /** 新建单条数据接口路径。 */
    private static final String CREATE_PATH = "/api/v5/app/entry/data/create";
    /** 新建多条数据接口路径。 */
    private static final String BATCH_CREATE_PATH = "/api/v5/app/entry/data/batch_create";
    /** 修改单条数据接口路径。 */
    private static final String UPDATE_PATH = "/api/v5/app/entry/data/update";
    /** 修改多条数据接口路径。 */
    private static final String BATCH_UPDATE_PATH = "/api/v5/app/entry/data/batch_update";
    /** 删除单条数据接口路径。 */
    private static final String DELETE_PATH = "/api/v5/app/entry/data/delete";
    /** 删除多条数据接口路径。 */
    private static final String BATCH_DELETE_PATH = "/api/v5/app/entry/data/batch_delete";
    /** 自动翻页时每页条数，取接口允许的最大值以减少请求次数。 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建数据入口。
     *
     * @param transport 共享协议传输层
     */
    public DataClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询单条数据。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @return 数据记录
     */
    public DataRecord get(String appId, String entryId, String dataId) {
        RecordPayload payload = transport.post(transport.endpoint(GET_PATH), new DataIdPayload(
                JiandaoyunValidationUtils.requireAppId(appId),
                JiandaoyunValidationUtils.requireEntryId(entryId),
                JiandaoyunValidationUtils.requireNotBlank(dataId, "dataId"),
                null
        ), RecordPayload.class);
        return DataRecord.of(payload.data());
    }

    /**
     * 查询一页数据。
     *
     * @param request 查询条件与分页
     * @return 本页数据，按数据 ID 升序
     */
    public List<DataRecord> list(ListDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return listPage(request, request.dataId(), request.limit());
    }

    /**
     * 按数据 ID 游标自动翻页，查询满足条件的全部数据。
     *
     * <p>每页按请求的 {@code limit} 取数（未配置时取 100），直到某页不足一页为止；请求中的
     * {@code dataId} 作为起始游标。数据量大时注意内存占用与接口频率限制（30 次/秒）。</p>
     *
     * @param request 查询条件
     * @return 全部数据
     */
    public List<DataRecord> listAll(ListDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        int pageSize = request.limit() == null ? MAX_PAGE_SIZE : request.limit();
        List<DataRecord> result = new ArrayList<>();
        String cursor = request.dataId();
        while (true) {
            List<DataRecord> page = listPage(request, cursor, pageSize);
            result.addAll(page);
            if (page.size() < pageSize) {
                return result;
            }
            cursor = page.getLast().id();
            if (cursor == null) {
                // 缺少 _id 时无法继续推进游标，继续请求只会重复取同一页
                return result;
            }
        }
    }

    /**
     * 新建单条数据，不发起流程、不触发智能助手。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param data 字段名到值的映射，值无需包装
     * @return 提交后的完整数据
     */
    public DataRecord create(String appId, String entryId, Map<String, ?> data) {
        return create(CreateDataRequest.builder().appId(appId).entryId(entryId).data(data).build());
    }

    /**
     * 新建单条数据。
     *
     * @param request 新建参数
     * @return 提交后的完整数据
     */
    public DataRecord create(CreateDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        RecordPayload payload = transport.post(transport.endpoint(CREATE_PATH), new CreatePayload(
                request.appId(),
                request.entryId(),
                JiandaoyunDataUtils.wrap(request.data()),
                request.dataCreator(),
                request.startWorkflow(),
                request.startTrigger(),
                request.transactionId()
        ), RecordPayload.class);
        return DataRecord.of(payload.data());
    }

    /**
     * 新建多条数据。
     *
     * @param request 新建参数
     * @return 成功条数与数据 ID
     */
    public BatchCreateResult batchCreate(BatchCreateDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(BATCH_CREATE_PATH), new BatchCreatePayload(
                request.appId(),
                request.entryId(),
                JiandaoyunDataUtils.wrapAll(request.dataList()),
                request.dataCreator(),
                request.startWorkflow(),
                request.transactionId()
        ), BatchCreateResult.class);
    }

    /**
     * 修改单条数据，不触发智能助手。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param data 待修改的字段，值无需包装
     * @return 修改后的完整数据
     */
    public DataRecord update(String appId, String entryId, String dataId, Map<String, ?> data) {
        return update(UpdateDataRequest.builder()
                .appId(appId).entryId(entryId).dataId(dataId).data(data).build());
    }

    /**
     * 修改单条数据。
     *
     * @param request 修改参数
     * @return 修改后的完整数据
     */
    public DataRecord update(UpdateDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        RecordPayload payload = transport.post(transport.endpoint(UPDATE_PATH), new UpdatePayload(
                request.appId(),
                request.entryId(),
                request.dataId(),
                JiandaoyunDataUtils.wrap(request.data()),
                request.startTrigger(),
                request.transactionId()
        ), RecordPayload.class);
        return DataRecord.of(payload.data());
    }

    /**
     * 把同一组字段值写入多条数据。
     *
     * @param request 修改参数
     * @return 成功修改的条数
     */
    public int batchUpdate(BatchUpdateDataRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(BATCH_UPDATE_PATH), new BatchUpdatePayload(
                request.appId(),
                request.entryId(),
                request.dataIds(),
                JiandaoyunDataUtils.wrap(request.data()),
                request.transactionId()
        ), CountPayload.class).successCount();
    }

    /**
     * 删除单条数据，不触发智能助手。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     */
    public void delete(String appId, String entryId, String dataId) {
        deleteData(appId, entryId, dataId, null);
    }

    /**
     * 删除单条数据。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param startTrigger 是否触发智能助手
     */
    public void delete(String appId, String entryId, String dataId, boolean startTrigger) {
        deleteData(appId, entryId, dataId, startTrigger);
    }

    /**
     * 删除多条数据，单次最多 100 条。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataIds 数据 ID 列表
     * @return 成功删除的条数
     */
    public int batchDelete(String appId, String entryId, List<String> dataIds) {
        return transport.post(transport.endpoint(BATCH_DELETE_PATH), new BatchDeletePayload(
                JiandaoyunValidationUtils.requireAppId(appId),
                JiandaoyunValidationUtils.requireEntryId(entryId),
                List.copyOf(ValidationUtils.requireNonNull(dataIds, "dataIds must not be null"))
        ), CountPayload.class).successCount();
    }

    /**
     * 按指定游标和条数查询一页数据。
     *
     * @param request 查询条件
     * @param dataId 翻页游标
     * @param limit 取数条数
     * @return 本页数据
     */
    private List<DataRecord> listPage(ListDataRequest request, @Nullable String dataId, @Nullable Integer limit) {
        ListPayload payload = transport.post(transport.endpoint(LIST_PATH), new ListRequestPayload(
                request.appId(),
                request.entryId(),
                dataId,
                request.fields(),
                request.filter(),
                limit
        ), ListPayload.class);
        return payload.data().stream().map(DataRecord::of).toList();
    }

    /**
     * 发送单条删除请求。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param startTrigger 是否触发智能助手；为 {@code null} 时省略
     */
    private void deleteData(String appId, String entryId, String dataId, @Nullable Boolean startTrigger) {
        transport.postVoid(transport.endpoint(DELETE_PATH), new DataIdPayload(
                JiandaoyunValidationUtils.requireAppId(appId),
                JiandaoyunValidationUtils.requireEntryId(entryId),
                JiandaoyunValidationUtils.requireNotBlank(dataId, "dataId"),
                startTrigger
        ));
    }

    /**
     * 单条数据定位请求正文，用于查询和删除。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param startTrigger 是否触发智能助手，仅删除使用
     */
    private record DataIdPayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_id") String dataId,
            @JsonProperty("is_start_trigger") @Nullable Boolean startTrigger
    ) {
    }

    /**
     * 多条数据查询请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 翻页游标
     * @param fields 查询字段
     * @param filter 过滤条件
     * @param limit 取数条数
     */
    private record ListRequestPayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_id") @Nullable String dataId,
            @Nullable List<String> fields,
            @Nullable DataFilter filter,
            @Nullable Integer limit
    ) {
    }

    /**
     * 新建单条数据请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param data 已包装的数据
     * @param dataCreator 数据提交人
     * @param startWorkflow 是否发起流程
     * @param startTrigger 是否触发智能助手
     * @param transactionId 事务 ID
     */
    private record CreatePayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            Map<String, Object> data,
            @JsonProperty("data_creator") @Nullable String dataCreator,
            @JsonProperty("is_start_workflow") @Nullable Boolean startWorkflow,
            @JsonProperty("is_start_trigger") @Nullable Boolean startTrigger,
            @JsonProperty("transaction_id") @Nullable String transactionId
    ) {
    }

    /**
     * 新建多条数据请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataList 已包装的数据列表
     * @param dataCreator 数据提交人
     * @param startWorkflow 是否发起流程
     * @param transactionId 事务 ID
     */
    private record BatchCreatePayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_list") List<Map<String, Object>> dataList,
            @JsonProperty("data_creator") @Nullable String dataCreator,
            @JsonProperty("is_start_workflow") @Nullable Boolean startWorkflow,
            @JsonProperty("transaction_id") @Nullable String transactionId
    ) {
    }

    /**
     * 修改单条数据请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param data 已包装的数据
     * @param startTrigger 是否触发智能助手
     * @param transactionId 事务 ID
     */
    private record UpdatePayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_id") String dataId,
            Map<String, Object> data,
            @JsonProperty("is_start_trigger") @Nullable Boolean startTrigger,
            @JsonProperty("transaction_id") @Nullable String transactionId
    ) {
    }

    /**
     * 修改多条数据请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataIds 数据 ID 列表
     * @param data 已包装的数据
     * @param transactionId 事务 ID
     */
    private record BatchUpdatePayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_ids") List<String> dataIds,
            Map<String, Object> data,
            @JsonProperty("transaction_id") @Nullable String transactionId
    ) {
    }

    /**
     * 删除多条数据请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataIds 数据 ID 列表
     */
    private record BatchDeletePayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("data_ids") List<String> dataIds
    ) {
    }

    /**
     * 单条数据响应正文。
     *
     * @param data 原始数据
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecordPayload(Map<String, Object> data) {
    }

    /**
     * 多条数据响应正文。
     *
     * @param data 原始数据列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListPayload(List<Map<String, Object>> data) {
    }

    /**
     * 批量操作的成功条数响应正文。
     *
     * @param successCount 成功条数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CountPayload(@JsonProperty("success_count") int successCount) {
    }
}
