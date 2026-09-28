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

import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunApiException;
import com.zhengshuyun.lava.jiandaoyun.workflow.AddSignType;
import com.zhengshuyun.lava.jiandaoyun.workflow.ApprovalComment;
import com.zhengshuyun.lava.jiandaoyun.workflow.CcReadStatus;
import com.zhengshuyun.lava.jiandaoyun.workflow.ListCcRequest;
import com.zhengshuyun.lava.jiandaoyun.workflow.ListWorkflowLogsRequest;
import com.zhengshuyun.lava.jiandaoyun.workflow.ListWorkflowTasksRequest;
import com.zhengshuyun.lava.jiandaoyun.workflow.RollbackBackType;
import com.zhengshuyun.lava.jiandaoyun.workflow.RollbackTaskRequest;
import com.zhengshuyun.lava.jiandaoyun.workflow.WorkflowCcPage;
import com.zhengshuyun.lava.jiandaoyun.workflow.WorkflowInstance;
import com.zhengshuyun.lava.jiandaoyun.workflow.WorkflowLog;
import com.zhengshuyun.lava.jiandaoyun.workflow.WorkflowTaskPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云流程客户端的请求组装、响应解析和 2xx 业务失败映射测试。
 */
class WorkflowClientTest extends ClientTestSupport {

    /**
     * 验证查询流程实例携带任务返回方式并解析实例与待办。
     */
    @Test
    void getInstanceParsesTasks() {
        server.enqueue(200, "{\"app_id\":\"a1\",\"form_id\":\"f1\",\"form_title\":\"借用\",\"instance_id\":\"i1\","
                + "\"create_time\":\"2022-10-26T13:11:45.087Z\",\"finish_time\":null,\"status\":0,"
                + "\"creator\":{\"username\":\"xiaojian\",\"name\":\"小简\"},"
                + "\"tasks\":[{\"app_id\":\"a1\",\"form_id\":\"f1\",\"instance_id\":\"i1\",\"task_id\":\"t1\","
                + "\"flow_id\":2,\"flow_name\":\"审批\",\"assignee\":{\"username\":\"xiaoyun\"},\"status\":0}]}");

        WorkflowInstance instance = client.workflows().getInstance("i1", true);

        assertRequest("/api/v6/workflow/instance/get", "{\"instance_id\":\"i1\",\"tasks_type\":1}");
        assertEquals("i1", instance.instanceId());
        assertEquals(Instant.parse("2022-10-26T13:11:45.087Z"), instance.createTime());
        assertNull(instance.finishTime());
        assertEquals("xiaojian", instance.creator().username());
        assertEquals("t1", instance.tasks().getFirst().taskId());
        assertEquals(2, instance.tasks().getFirst().flowId());
        assertEquals("xiaoyun", instance.tasks().getFirst().assignee().username());
    }

    /**
     * 验证流程接口在 2xx 下返回 status=failure 时抛出携带错误码与描述的 API 异常。
     */
    @Test
    void successStatusWithFailureBodyThrowsApiException() {
        server.enqueue(200, "{\"code\":4008,\"message\":\"操作失败,流程已经关闭\",\"status\":\"failure\"}");

        JiandaoyunApiException exception = assertThrows(JiandaoyunApiException.class,
                () -> client.workflows().closeInstance("i1"));

        assertEquals(200, exception.statusCode());
        assertEquals(4008, exception.code());
        assertEquals("操作失败,流程已经关闭", exception.apiMessage());
    }

    /**
     * 验证实例结束与激活的请求正文。
     */
    @Test
    void closeAndActivateInstance() {
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, "{\"status\":\"success\"}");

        client.workflows().closeInstance("i1");
        client.workflows().activateInstance("i1", 3);

        assertRequest("/api/v1/workflow/instance/close", "{\"instance_id\":\"i1\"}");
        assertRequest("/api/v1/workflow/instance/activate", "{\"instance_id\":\"i1\",\"flow_id\":3}");
    }

    /**
     * 验证流程日志默认查询审批意见类型并解析附件。
     */
    @Test
    void listLogsDefaultsToCommentType() {
        server.enqueue(200, "{\"logs\":[{\"flow_id\":1,\"comment\":\"同意\","
                + "\"attachments\":[{\"name\":\"a.pdf\",\"size\":12,\"mime\":\"application/pdf\"}]}]}");

        List<WorkflowLog> logs = client.workflows().listLogs(
                ListWorkflowLogsRequest.builder().instanceId("i1").limit(10).build());

        assertRequest("/api/v1/workflow/instance/logs",
                "{\"instance_id\":\"i1\",\"types\":[\"comment\"],\"limit\":10}");
        assertEquals("同意", logs.getFirst().comment());
        assertEquals(12L, logs.getFirst().attachments().getFirst().size());
    }

    /**
     * 验证审批意见接口把应用、表单、数据 ID 放入路径。
     */
    @Test
    void listApprovalCommentsUsesPathParameters() {
        server.enqueue(200, "{\"approveCommentList\":[{\"flowNodeName\":\"审批\",\"flowAction\":\"forward\","
                + "\"comment\":\"同意\",\"operator\":{\"username\":\"xiaoyun\"}}]}");

        List<ApprovalComment> comments = client.workflows().listApprovalComments(APP_ID, ENTRY_ID, "d1", 5);

        assertRequest("/api/v1/app/" + APP_ID + "/entry/" + ENTRY_ID + "/data/d1/approval_comments",
                "{\"skip\":5}");
        assertEquals("forward", comments.getFirst().flowAction());
        assertEquals("xiaoyun", comments.getFirst().operator().username());
    }

    /**
     * 验证待办列表的游标请求与分页结果。
     */
    @Test
    void listTasksParsesPage() {
        server.enqueue(200, "{\"has_more\":true,\"tasks\":[{\"app_id\":\"a1\",\"form_id\":\"f1\","
                + "\"instance_id\":\"i1\",\"task_id\":\"t9\",\"title\":\"审批\"}]}");

        WorkflowTaskPage page = client.workflows().listTasks(
                ListWorkflowTasksRequest.builder().username("xiaoyun").taskId("t8").limit(1).build());

        assertRequest("/api/v6/workflow/task/list", "{\"username\":\"xiaoyun\",\"task_id\":\"t8\",\"limit\":1}");
        assertTrue(page.hasMore());
        assertEquals("t9", page.tasks().getFirst().taskId());
    }

    /**
     * 验证提交、否决、转交、加签、撤回、回退的请求路径与正文，未填写的意见被省略。
     */
    @Test
    void taskActionsSendExpectedBodies() {
        for (int i = 0; i < 6; i++) {
            server.enqueue(200, "{\"status\":\"success\"}");
        }

        client.workflows().approveTask("u1", "i1", "t1");
        client.workflows().rejectTask("u1", "i1", "t1", "不同意");
        client.workflows().transferTask("u1", "i1", "t1", "u2", null);
        client.workflows().addSign("u1", "i1", "t1", AddSignType.PARALLEL, List.of("u3"), null);
        client.workflows().revokeTask("u1", "i1", null, null);
        client.workflows().rollbackTask(RollbackTaskRequest.builder()
                .username("u1").instanceId("i1").taskId("t1")
                .flowId(0).backType(RollbackBackType.DIRECT).comment("退回").build());

        String base = "\"username\":\"u1\",\"instance_id\":\"i1\"";
        assertRequest("/api/v1/workflow/task/approve", "{" + base + ",\"task_id\":\"t1\"}");
        assertRequest("/api/v1/workflow/task/reject", "{" + base + ",\"task_id\":\"t1\",\"comment\":\"不同意\"}");
        assertRequest("/api/v1/workflow/task/transfer",
                "{" + base + ",\"task_id\":\"t1\",\"transfer_username\":\"u2\"}");
        assertRequest("/api/v2/workflow/task/add_sign",
                "{" + base + ",\"task_id\":\"t1\",\"add_sign_type\":2,\"add_sign_usernames\":[\"u3\"]}");
        assertRequest("/api/v2/workflow/task/revoke", "{" + base + "}");
        assertRequest("/api/v2/workflow/task/rollback",
                "{" + base + ",\"task_id\":\"t1\",\"flow_id\":0,\"comment\":\"退回\",\"back_type\":2}");
    }

    /**
     * 验证抄送列表的阅读状态按协议取值序列化。
     */
    @Test
    void listCcSendsReadStatus() {
        server.enqueue(200, "{\"has_more\":false,\"cc_list\":[{\"app_id\":\"a1\",\"form_id\":\"f1\","
                + "\"task_id\":\"c1\",\"instance_id\":\"i1\",\"status\":0}]}");

        WorkflowCcPage page = client.workflows().listCc(ListCcRequest.builder()
                .username("u1").readStatus(CcReadStatus.UNREAD).skip(0).limit(20).build());

        assertRequest("/api/v1/workflow/cc/list",
                "{\"username\":\"u1\",\"skip\":0,\"limit\":20,\"read_status\":\"unread\"}");
        assertEquals("c1", page.ccList().getFirst().taskId());
    }

    /**
     * 验证缺少必填参数时在发请求前即被拒绝。
     */
    @Test
    void rejectsMissingRequiredArguments() {
        assertThrows(IllegalArgumentException.class, () -> client.workflows().getInstance(" "));
        assertThrows(IllegalArgumentException.class, () -> client.workflows().approveTask("u1", "i1", " "));
        assertThrows(IllegalArgumentException.class,
                () -> RollbackTaskRequest.builder().username("u1").instanceId("i1").build());
        assertThrows(IllegalArgumentException.class, () -> ListCcRequest.builder().build());
    }
}
