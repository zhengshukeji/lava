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

package com.zhengshuyun.lava.jiandaoyun.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云应用查询客户端。
 */
public final class ApplicationClient {
    /** 用户应用查询接口 v5 的 API 路径。 */
    private static final String APP_LIST_PATH = "/api/v5/app/list";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建应用查询入口。
     *
     * @param transport 共享协议传输层
     */
    public ApplicationClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询当前 API Key 授权范围内的应用列表。
     *
     * @param request 分页参数
     * @return 应用信息列表
     */
    public List<Application> list(ListApplicationsRequest request) {
        transport.ensureOpen();
        request = ValidationUtils.requireNonNull(request, "request must not be null");
        ApplicationListPayload payload = transport.post(
                transport.endpoint(APP_LIST_PATH),
                new ListPayload(request.limit(), request.skip()),
                ApplicationListPayload.class
        );
        return payload.apps();
    }

    /**
     * 应用列表响应正文结构。
     *
     * @param apps 应用信息列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApplicationListPayload(List<Application> apps) {
    }

    /**
     * 应用列表请求正文结构；未配置的分页字段序列化时省略。
     *
     * @param limit 单次取数条数
     * @param skip 需跳过的数据条数
     */
    private record ListPayload(@Nullable Integer limit, @Nullable Integer skip) {
    }
}
