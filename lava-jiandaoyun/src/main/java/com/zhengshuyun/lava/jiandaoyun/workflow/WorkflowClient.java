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

package com.zhengshuyun.lava.jiandaoyun.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云流程客户端：流程实例、流程日志、待办处理和抄送查询。
 *
 * <p>流程实例 ID 与数据 ID 相同。待办操作的 {@code username} 必须是该待办的当前处理人，
 * 且与 {@code taskId} 一一对应；操作失败（如流程已结束）抛
 * {@link com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunApiException}。</p>
 */
public final class WorkflowClient {
    /** 查询流程实例信息接口路径。 */
    private static final String INSTANCE_GET_PATH = "/api/v6/workflow/instance/get";
    /** 结束流程实例接口路径。 */
    private static final String INSTANCE_CLOSE_PATH = "/api/v1/workflow/instance/close";
    /** 激活流程实例接口路径。 */
    private static final String INSTANCE_ACTIVATE_PATH = "/api/v1/workflow/instance/activate";
    /** 查询流程日志接口路径。 */
    private static final String INSTANCE_LOGS_PATH = "/api/v1/workflow/instance/logs";
    /** 查询审批意见接口路径前缀，后接应用、表单、数据三段路径参数。 */
    private static final String APPROVAL_COMMENTS_PREFIX = "/api/v1/app";
    /** 查询待办接口路径。 */
    private static final String TASK_LIST_PATH = "/api/v6/workflow/task/list";
    /** 待办提交接口路径。 */
    private static final String TASK_APPROVE_PATH = "/api/v1/workflow/task/approve";
    /** 待办回退接口路径。 */
    private static final String TASK_ROLLBACK_PATH = "/api/v2/workflow/task/rollback";
    /** 待办转交接口路径。 */
    private static final String TASK_TRANSFER_PATH = "/api/v1/workflow/task/transfer";
    /** 待办加签接口路径。 */
    private static final String TASK_ADD_SIGN_PATH = "/api/v2/workflow/task/add_sign";
    /** 待办撤回接口路径。 */
    private static final String TASK_REVOKE_PATH = "/api/v2/workflow/task/revoke";
    /** 待办否决接口路径。 */
    private static final String TASK_REJECT_PATH = "/api/v1/workflow/task/reject";
    /** 查询抄送列表接口路径。 */
    private static final String CC_LIST_PATH = "/api/v1/workflow/cc/list";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建流程入口。
     *
     * @param transport 共享协议传输层
     */
    public WorkflowClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询流程实例信息，不返回待办任务。
     *
     * @param instanceId 流程实例 ID
     * @return 流程实例
     */
    public WorkflowInstance getInstance(String instanceId) {
        return getInstance(instanceId, false);
    }

    /**
     * 查询流程实例信息。
     *
     * @param instanceId 流程实例 ID
     * @param includeTasks 是否同时返回全部待办任务
     * @return 流程实例
     */
    public WorkflowInstance getInstance(String instanceId, boolean includeTasks) {
        return transport.post(transport.endpoint(INSTANCE_GET_PATH), new InstanceGetPayload(
                requireInstanceId(instanceId),
                includeTasks ? 1 : 0
        ), WorkflowInstance.class);
    }

    /**
     * 结束流程实例。
     *
     * @param instanceId 流程实例 ID
     */
    public void closeInstance(String instanceId) {
        transport.postVoid(transport.endpoint(INSTANCE_CLOSE_PATH), new InstancePayload(requireInstanceId(instanceId)));
    }

    /**
     * 激活已结束的流程实例到指定节点。
     *
     * @param instanceId 流程实例 ID
     * @param flowId 激活的节点 ID
     */
    public void activateInstance(String instanceId, int flowId) {
        transport.postVoid(transport.endpoint(INSTANCE_ACTIVATE_PATH),
                new ActivatePayload(requireInstanceId(instanceId), flowId));
    }

    /**
     * 查询流程日志。
     *
     * @param request 查询参数
     * @return 流程日志
     */
    public List<WorkflowLog> listLogs(ListWorkflowLogsRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(INSTANCE_LOGS_PATH), new LogsPayload(
                request.instanceId(),
                request.types(),
                request.limit(),
                request.skip()
        ), LogListPayload.class).logs();
    }

    /**
     * 查询流程表单数据的审批意见，单次最多返回 100 条。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @return 审批意见
     */
    public List<ApprovalComment> listApprovalComments(String appId, String entryId, String dataId) {
        return queryApprovalComments(appId, entryId, dataId, null);
    }

    /**
     * 跳过指定条数后查询审批意见，单次最多返回 100 条。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param skip 跳过条数
     * @return 审批意见
     */
    public List<ApprovalComment> listApprovalComments(String appId, String entryId, String dataId, int skip) {
        ValidationUtils.requireTrue(skip >= 0, "skip must not be negative");
        return queryApprovalComments(appId, entryId, dataId, skip);
    }

    /**
     * 查询成员的待办。
     *
     * @param request 查询参数
     * @return 待办分页结果
     */
    public WorkflowTaskPage listTasks(ListWorkflowTasksRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(TASK_LIST_PATH), new TaskListPayload(
                request.username(),
                request.taskId(),
                request.limit()
        ), WorkflowTaskPage.class);
    }

    /**
     * 提交待办（同意）。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     */
    public void approveTask(String username, String instanceId, String taskId) {
        approveTask(username, instanceId, taskId, null);
    }

    /**
     * 提交待办（同意）并填写审批意见。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param comment 审批意见；为 {@code null} 时不填写
     */
    public void approveTask(String username, String instanceId, String taskId, @Nullable String comment) {
        transport.postVoid(transport.endpoint(TASK_APPROVE_PATH),
                TaskPayload.of(username, instanceId, taskId, comment));
    }

    /**
     * 否决待办。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     */
    public void rejectTask(String username, String instanceId, String taskId) {
        rejectTask(username, instanceId, taskId, null);
    }

    /**
     * 否决待办并填写审批意见；节点要求必填意见时 {@code comment} 不能为空。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param comment 审批意见；为 {@code null} 时不填写
     */
    public void rejectTask(String username, String instanceId, String taskId, @Nullable String comment) {
        transport.postVoid(transport.endpoint(TASK_REJECT_PATH),
                TaskPayload.of(username, instanceId, taskId, comment));
    }

    /**
     * 回退待办。
     *
     * @param request 回退参数
     */
    public void rollbackTask(RollbackTaskRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        transport.postVoid(transport.endpoint(TASK_ROLLBACK_PATH), new RollbackPayload(
                request.username(),
                request.instanceId(),
                request.taskId(),
                request.flowId(),
                request.comment(),
                request.backType()
        ));
    }

    /**
     * 把待办转交给其他成员。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param transferUsername 接收人成员编号
     * @param comment 审批意见；为 {@code null} 时不填写
     */
    public void transferTask(String username, String instanceId, String taskId, String transferUsername,
                             @Nullable String comment) {
        transport.postVoid(transport.endpoint(TASK_TRANSFER_PATH), new TransferPayload(
                JiandaoyunValidationUtils.requireNotBlank(username, "username"),
                requireInstanceId(instanceId),
                JiandaoyunValidationUtils.requireNotBlank(taskId, "taskId"),
                JiandaoyunValidationUtils.requireNotBlank(transferUsername, "transferUsername"),
                comment
        ));
    }

    /**
     * 为待办加签。
     *
     * @param username 当前处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param type 加签方式
     * @param addSignUsernames 加签人成员编号
     * @param comment 审批意见；为 {@code null} 时不填写
     */
    public void addSign(String username, String instanceId, String taskId, AddSignType type,
                        List<String> addSignUsernames, @Nullable String comment) {
        transport.postVoid(transport.endpoint(TASK_ADD_SIGN_PATH), new AddSignPayload(
                JiandaoyunValidationUtils.requireNotBlank(username, "username"),
                requireInstanceId(instanceId),
                JiandaoyunValidationUtils.requireNotBlank(taskId, "taskId"),
                ValidationUtils.requireNonNull(type, "type must not be null"),
                List.copyOf(ValidationUtils.requireNonNull(addSignUsernames, "addSignUsernames must not be null")),
                comment
        ));
    }

    /**
     * 撤回已提交的待办。
     *
     * @param username 处理该待办的成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 要撤回的待办 ID；为 {@code null} 时撤回发起节点
     * @param comment 撤回理由；节点为「仅发起人可撤回」且流程已流转到非相邻节点时必填
     */
    public void revokeTask(String username, String instanceId, @Nullable String taskId, @Nullable String comment) {
        transport.postVoid(transport.endpoint(TASK_REVOKE_PATH), new TaskPayload(
                JiandaoyunValidationUtils.requireNotBlank(username, "username"),
                requireInstanceId(instanceId),
                taskId,
                comment
        ));
    }

    /**
     * 查询成员的抄送列表。
     *
     * @param request 查询参数
     * @return 抄送分页结果
     */
    public WorkflowCcPage listCc(ListCcRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(CC_LIST_PATH), new CcListPayload(
                request.username(),
                request.skip(),
                request.limit(),
                request.readStatus()
        ), WorkflowCcPage.class);
    }

    /**
     * 发送审批意见查询；应用、表单、数据 ID 位于路径中。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param dataId 数据 ID
     * @param skip 跳过条数；为 {@code null} 时省略
     * @return 审批意见
     */
    private List<ApprovalComment> queryApprovalComments(String appId, String entryId, String dataId,
                                                        @Nullable Integer skip) {
        List<String> segments = List.of(
                JiandaoyunValidationUtils.requireAppId(appId),
                "entry",
                JiandaoyunValidationUtils.requireEntryId(entryId),
                "data",
                JiandaoyunValidationUtils.requireNotBlank(dataId, "dataId")
        );
        return transport.post(
                transport.endpoint(APPROVAL_COMMENTS_PREFIX, segments, "approval_comments"),
                new SkipPayload(skip),
                ApprovalCommentListPayload.class
        ).approveCommentList();
    }

    /**
     * 校验流程实例 ID。
     *
     * @param instanceId 流程实例 ID
     * @return 原值
     */
    private static String requireInstanceId(String instanceId) {
        return JiandaoyunValidationUtils.requireNotBlank(instanceId, "instanceId");
    }

    /**
     * 查询流程实例请求正文。
     *
     * @param instanceId 流程实例 ID
     * @param tasksType 任务返回方式：0 不返回，1 返回全部
     */
    private record InstanceGetPayload(
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("tasks_type") int tasksType
    ) {
    }

    /**
     * 仅含流程实例 ID 的请求正文。
     *
     * @param instanceId 流程实例 ID
     */
    private record InstancePayload(@JsonProperty("instance_id") String instanceId) {
    }

    /**
     * 激活流程实例请求正文。
     *
     * @param instanceId 流程实例 ID
     * @param flowId 激活的节点 ID
     */
    private record ActivatePayload(
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("flow_id") int flowId
    ) {
    }

    /**
     * 流程日志请求正文。
     *
     * @param instanceId 流程实例 ID
     * @param types 日志类型
     * @param limit 每页条数
     * @param skip 跳过条数
     */
    private record LogsPayload(
            @JsonProperty("instance_id") String instanceId,
            List<String> types,
            @Nullable Integer limit,
            @Nullable Integer skip
    ) {
    }

    /**
     * 仅含跳过条数的请求正文。
     *
     * @param skip 跳过条数
     */
    private record SkipPayload(@Nullable Integer skip) {
    }

    /**
     * 待办列表请求正文。
     *
     * @param username 成员编号
     * @param taskId 翻页游标
     * @param limit 每页条数
     */
    private record TaskListPayload(
            String username,
            @JsonProperty("task_id") @Nullable String taskId,
            @Nullable Integer limit
    ) {
    }

    /**
     * 通用待办操作请求正文，用于提交、否决和撤回。
     *
     * @param username 处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param comment 审批意见
     */
    private record TaskPayload(
            String username,
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("task_id") @Nullable String taskId,
            @Nullable String comment
    ) {
        /**
         * 校验必填项并创建请求正文。
         *
         * @param username 处理人成员编号
         * @param instanceId 流程实例 ID
         * @param taskId 待办 ID
         * @param comment 审批意见
         * @return 请求正文
         */
        static TaskPayload of(String username, String instanceId, String taskId, @Nullable String comment) {
            return new TaskPayload(
                    JiandaoyunValidationUtils.requireNotBlank(username, "username"),
                    requireInstanceId(instanceId),
                    JiandaoyunValidationUtils.requireNotBlank(taskId, "taskId"),
                    comment
            );
        }
    }

    /**
     * 待办回退请求正文。
     *
     * @param username 处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param flowId 回退目标节点 ID
     * @param comment 回退意见
     * @param backType 回退人选择
     */
    private record RollbackPayload(
            String username,
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("flow_id") @Nullable Integer flowId,
            @Nullable String comment,
            @JsonProperty("back_type") @Nullable RollbackBackType backType
    ) {
    }

    /**
     * 待办转交请求正文。
     *
     * @param username 处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param transferUsername 接收人成员编号
     * @param comment 审批意见
     */
    private record TransferPayload(
            String username,
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("transfer_username") String transferUsername,
            @Nullable String comment
    ) {
    }

    /**
     * 待办加签请求正文。
     *
     * @param username 处理人成员编号
     * @param instanceId 流程实例 ID
     * @param taskId 待办 ID
     * @param addSignType 加签方式
     * @param addSignUsernames 加签人
     * @param comment 审批意见
     */
    private record AddSignPayload(
            String username,
            @JsonProperty("instance_id") String instanceId,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("add_sign_type") AddSignType addSignType,
            @JsonProperty("add_sign_usernames") List<String> addSignUsernames,
            @Nullable String comment
    ) {
    }

    /**
     * 抄送列表请求正文。
     *
     * @param username 成员编号
     * @param skip 跳过条数
     * @param limit 每页条数
     * @param readStatus 阅读状态
     */
    private record CcListPayload(
            String username,
            @Nullable Integer skip,
            @Nullable Integer limit,
            @JsonProperty("read_status") @Nullable CcReadStatus readStatus
    ) {
    }

    /**
     * 流程日志响应正文。
     *
     * @param logs 日志列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LogListPayload(List<WorkflowLog> logs) {
    }

    /**
     * 审批意见响应正文。
     *
     * @param approveCommentList 审批意见列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApprovalCommentListPayload(List<ApprovalComment> approveCommentList) {
    }
}
