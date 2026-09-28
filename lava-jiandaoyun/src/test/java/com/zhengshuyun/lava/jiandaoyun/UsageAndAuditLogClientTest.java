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

import com.zhengshuyun.lava.jiandaoyun.auditlog.AuditLog;
import com.zhengshuyun.lava.jiandaoyun.auditlog.AuditLogDomain;
import com.zhengshuyun.lava.jiandaoyun.auditlog.AuditLogPage;
import com.zhengshuyun.lava.jiandaoyun.auditlog.ListAuditLogsRequest;
import com.zhengshuyun.lava.jiandaoyun.usage.AppUsagePage;
import com.zhengshuyun.lava.jiandaoyun.usage.ListAppUsageRequest;
import com.zhengshuyun.lava.jiandaoyun.usage.ListMemberUsageRequest;
import com.zhengshuyun.lava.jiandaoyun.usage.MemberUsagePage;
import com.zhengshuyun.lava.jiandaoyun.usage.UsageOverview;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云资源用量与审计日志客户端的请求组装与响应解析测试。
 */
class UsageAndAuditLogClientTest extends ClientTestSupport {

    /**
     * 验证平台用量的日期按 yyyy-MM-dd 发送，未指定日期时正文为空对象。
     */
    @Test
    void overviewSendsIsoDate() {
        String response = "{\"date\":\"2026-05-08\",\"metrics\":{\"app\":34,\"form_coop\":245,\"data\":353}}";
        server.enqueue(200, response);
        server.enqueue(200, response);

        UsageOverview overview = client.usage().overview(LocalDate.of(2026, 5, 8));
        client.usage().overview();

        assertRequest("/api/v1/corp_usage/overview", "{\"date\":\"2026-05-08\"}");
        assertRequest("/api/v1/corp_usage/overview", "{}");
        assertEquals(LocalDate.of(2026, 5, 8), overview.date());
        assertEquals(245L, overview.metrics().formCoop());
        assertNull(overview.metrics().bpa());
    }

    /**
     * 验证应用与成员维度用量的请求字段和带时区偏移的时间解析。
     */
    @Test
    void appAndMemberMetrics() {
        server.enqueue(200, "{\"date\":\"2026-05-08\",\"has_next\":true,\"items\":[{\"app_id\":\"a1\","
                + "\"app_name\":\"CRM\",\"creator\":{\"member_id\":\"m1\",\"name\":\"张三\"},"
                + "\"created_at\":\"2026-01-01T00:00:00+08:00\",\"metrics\":{\"form_workflow\":3}}]}");
        server.enqueue(200, "{\"date\":\"2026-05-08\",\"has_next\":false,\"items\":[{"
                + "\"member\":{\"member_id\":\"m1\",\"name\":\"张三\"},\"metrics\":{\"app\":2}}]}");

        AppUsagePage apps = client.usage().listAppMetrics(ListAppUsageRequest.builder()
                .appIds("a1").limit(20).build());
        MemberUsagePage members = client.usage().listMemberMetrics(ListMemberUsageRequest.builder()
                .date(LocalDate.of(2026, 5, 8)).memberIds(List.of("m1")).skip(0).build());

        assertRequest("/api/v1/corp_usage/app_metrics", "{\"app_ids\":[\"a1\"],\"limit\":20}");
        assertRequest("/api/v1/corp_usage/member_metrics",
                "{\"date\":\"2026-05-08\",\"member_ids\":[\"m1\"],\"skip\":0}");
        assertTrue(apps.hasNext());
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00+08:00").toInstant(),
                apps.items().getFirst().createdAt().toInstant());
        assertEquals("m1", apps.items().getFirst().creator().memberId());
        assertEquals(3L, apps.items().getFirst().metrics().formWorkflow());
        assertEquals(2L, members.items().getFirst().metrics().app());
    }

    /**
     * 验证审计日志范围查询发送空对象。
     */
    @Test
    void listDomains() {
        server.enqueue(200, "{\"domains\":[{\"domain\":\"login\",\"event_types\":[\"auth.session.login\"]}]}");

        List<AuditLogDomain> domains = client.auditLogs().listDomains();

        assertRequest("/api/v1/audit_log/domains", "{}");
        assertEquals(new AuditLogDomain("login", List.of("auth.session.login")), domains.getFirst());
    }

    /**
     * 验证审计日志明细的时间范围格式化为 UTC 毫秒，过滤条件仅在配置时发送。
     */
    @Test
    void listAuditLogsFormatsTimeRangeAndFilters() {
        String response = "{\"has_more\":true,\"cursor\":\"c2\",\"items\":[{\"event_id\":\"e1\","
                + "\"event_time\":\"2026-05-11T09:09:38.792Z\",\"event_type\":\"data.record.print\","
                + "\"actor\":{\"type\":\"user\",\"id\":\"u1\",\"user_agent\":\"Mozilla/5.0\"},"
                + "\"resource\":{\"type\":\"form\",\"parent_type\":\"app\"},"
                + "\"event\":{\"action\":\"print\",\"outcome\":\"success\"},\"detail\":{\"count\":1}}]}";
        server.enqueue(200, response);
        server.enqueue(200, response);

        Instant start = Instant.parse("2026-04-01T00:00:00Z");
        Instant end = Instant.parse("2026-04-13T23:59:59Z");
        AuditLogPage page = client.auditLogs().list(ListAuditLogsRequest.builder()
                .domain("app_builder").timeRange(start, end)
                .eventTypes("data.record.create").appIds(List.of(APP_ID)).limit(100).build());
        client.auditLogs().list(ListAuditLogsRequest.builder()
                .domain("login").timeRange(start, end).cursor("c2").build());

        assertRequest("/api/v1/audit_log/list", "{\"domain\":\"app_builder\","
                + "\"time_range\":{\"start\":\"2026-04-01T00:00:00.000Z\",\"end\":\"2026-04-13T23:59:59.000Z\"},"
                + "\"event_types\":[\"data.record.create\"],\"limit\":100,"
                + "\"filters\":{\"app_ids\":[\"" + APP_ID + "\"]}}");
        assertRequest("/api/v1/audit_log/list", "{\"domain\":\"login\","
                + "\"time_range\":{\"start\":\"2026-04-01T00:00:00.000Z\",\"end\":\"2026-04-13T23:59:59.000Z\"},"
                + "\"cursor\":\"c2\"}");
        AuditLog log = page.items().getFirst();
        assertEquals("c2", page.cursor());
        assertEquals(Instant.parse("2026-05-11T09:09:38.792Z"), log.eventTime());
        assertEquals("Mozilla/5.0", log.actor().userAgent());
        assertEquals("app", log.resource().parentType());
        assertEquals("print", log.event().action());
        assertEquals(1, ((Number) log.detail().get("count")).intValue());
    }

    /**
     * 验证审计日志缺少范围或时间倒置时被拒绝。
     */
    @Test
    void auditLogRequestRejectsInvalidArguments() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> ListAuditLogsRequest.builder().timeRange(now, now).build());
        assertThrows(IllegalArgumentException.class,
                () -> ListAuditLogsRequest.builder().domain("login").build());
        assertThrows(IllegalArgumentException.class,
                () -> ListAuditLogsRequest.builder().domain("login").timeRange(now, now.minusSeconds(1)).build());
    }
}
