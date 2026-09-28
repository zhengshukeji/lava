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

package com.zhengshuyun.lava.jiandaoyun.usage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * 简道云资源用量客户端（旗舰版、专属版功能）。
 *
 * <p>统计日期默认前一天（T-1），最多回溯 180 天；接口频率限制为 1 次/秒。</p>
 */
public final class UsageClient {
    /** 平台资源用量接口路径。 */
    private static final String OVERVIEW_PATH = "/api/v1/corp_usage/overview";
    /** 应用资源用量接口路径。 */
    private static final String APP_METRICS_PATH = "/api/v1/corp_usage/app_metrics";
    /** 成员资源用量接口路径。 */
    private static final String MEMBER_METRICS_PATH = "/api/v1/corp_usage/member_metrics";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建资源用量入口。
     *
     * @param transport 共享协议传输层
     */
    public UsageClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询前一天的平台资源用量。
     *
     * @return 平台资源用量
     */
    public UsageOverview overview() {
        return queryOverview(null);
    }

    /**
     * 查询指定日期的平台资源用量。
     *
     * @param date 统计日期，不晚于前一天
     * @return 平台资源用量
     */
    public UsageOverview overview(LocalDate date) {
        return queryOverview(ValidationUtils.requireNonNull(date, "date must not be null"));
    }

    /**
     * 查询应用维度的资源用量。
     *
     * @param request 日期、应用与分页参数
     * @return 应用用量分页结果
     */
    public AppUsagePage listAppMetrics(ListAppUsageRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(APP_METRICS_PATH), new AppMetricsPayload(
                request.date(), request.appIds(), request.skip(), request.limit()
        ), AppUsagePage.class);
    }

    /**
     * 查询成员维度的资源用量。
     *
     * @param request 日期、成员与分页参数
     * @return 成员用量分页结果
     */
    public MemberUsagePage listMemberMetrics(ListMemberUsageRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(MEMBER_METRICS_PATH), new MemberMetricsPayload(
                request.date(), request.memberIds(), request.skip(), request.limit()
        ), MemberUsagePage.class);
    }

    /**
     * 发送平台资源用量查询。
     *
     * @param date 统计日期；为 {@code null} 时省略
     * @return 平台资源用量
     */
    private UsageOverview queryOverview(@Nullable LocalDate date) {
        return transport.post(transport.endpoint(OVERVIEW_PATH), new DatePayload(date), UsageOverview.class);
    }

    /**
     * 仅含统计日期的请求正文。
     *
     * @param date 统计日期
     */
    private record DatePayload(@Nullable LocalDate date) {
    }

    /**
     * 应用资源用量请求正文。
     *
     * @param date 统计日期
     * @param appIds 应用 ID
     * @param skip 跳过条数
     * @param limit 每页条数
     */
    private record AppMetricsPayload(
            @Nullable LocalDate date,
            @JsonProperty("app_ids") @Nullable List<String> appIds,
            @Nullable Integer skip,
            @Nullable Integer limit
    ) {
    }

    /**
     * 成员资源用量请求正文。
     *
     * @param date 统计日期
     * @param memberIds 成员 ID
     * @param skip 跳过条数
     * @param limit 每页条数
     */
    private record MemberMetricsPayload(
            @Nullable LocalDate date,
            @JsonProperty("member_ids") @Nullable List<String> memberIds,
            @Nullable Integer skip,
            @Nullable Integer limit
    ) {
    }
}
