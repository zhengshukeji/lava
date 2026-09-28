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

package com.zhengshuyun.lava.jiandaoyun.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云表单查询客户端。
 */
public final class FormClient {
    /** 用户表单查询接口 v5 的 API 路径。 */
    private static final String FORM_LIST_PATH = "/api/v5/app/entry/list";
    /** 表单字段查询接口 v5 的 API 路径。 */
    private static final String WIDGET_LIST_PATH = "/api/v5/app/entry/widget/list";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建表单查询入口。
     *
     * @param transport 共享协议传输层
     */
    public FormClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询指定应用下的全部表单。
     *
     * @param request 请求参数，含目标应用 ID 和分页
     * @return 表单信息列表
     */
    public List<Form> list(ListFormsRequest request) {
        transport.ensureOpen();
        request = ValidationUtils.requireNonNull(request, "request must not be null");
        FormListPayload payload = transport.post(
                transport.endpoint(FORM_LIST_PATH),
                new ListPayload(request.appId(), request.limit(), request.skip()),
                FormListPayload.class
        );
        return payload.forms();
    }

    /**
     * 查询指定表单的字段结构，包括业务字段、系统字段和数据最新修改时间。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @return 表单字段查询结果
     */
    public FormWidgets listWidgets(String appId, String entryId) {
        transport.ensureOpen();
        appId = JiandaoyunValidationUtils.requireAppId(appId);
        entryId = JiandaoyunValidationUtils.requireEntryId(entryId);
        return transport.post(
                transport.endpoint(WIDGET_LIST_PATH),
                new WidgetsPayload(appId, entryId),
                FormWidgets.class
        );
    }

    /**
     * 表单列表响应正文结构。
     *
     * @param forms 表单信息列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FormListPayload(List<Form> forms) {
    }

    /**
     * 表单列表请求正文结构；未配置的分页字段序列化时省略。
     *
     * @param appId 应用 ID
     * @param limit 单次取数条数
     * @param skip 需跳过的数据条数
     */
    private record ListPayload(
            @JsonProperty("app_id") String appId,
            @Nullable Integer limit,
            @Nullable Integer skip
    ) {
    }

    /**
     * 表单字段查询请求正文结构。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     */
    private record WidgetsPayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId
    ) {
    }
}
