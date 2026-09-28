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

package com.zhengshuyun.lava.jiandaoyun.department;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云通讯录部门客户端。根部门编号为 1。
 */
public final class DepartmentClient {
    /** 获取部门成员接口路径。 */
    private static final String MEMBER_LIST_PATH = "/api/v5/corp/department/user/list";
    /** 获取部门列表接口路径。 */
    private static final String LIST_PATH = "/api/v6/corp/department/list";
    /** 创建部门接口路径。 */
    private static final String CREATE_PATH = "/api/v6/corp/department/create";
    /** 修改部门接口路径。 */
    private static final String UPDATE_PATH = "/api/v6/corp/department/update";
    /** 删除部门接口路径。 */
    private static final String DELETE_PATH = "/api/v5/corp/department/delete";
    /** 按集成模式 ID 获取部门接口路径。 */
    private static final String DEPT_NO_GET_PATH = "/api/v6/corp/department/dept_no/get";
    /** 全量导入部门接口路径。 */
    private static final String IMPORT_PATH = "/api/v5/corp/department/import";
    /** 获取部门主管接口路径。 */
    private static final String MANAGER_GET_PATH = "/api/v6/corp/department/manager/get";
    /** 设置部门主管接口路径。 */
    private static final String MANAGER_UPDATE_PATH = "/api/v6/corp/department/manager/update";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建部门入口。
     *
     * @param transport 共享协议传输层
     */
    public DepartmentClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 获取部门成员。
     *
     * @param deptNo 部门编号
     * @param recursive 是否递归包含全部子部门的成员
     * @return 成员列表
     */
    public List<Member> listMembers(long deptNo, boolean recursive) {
        return transport.post(transport.endpoint(MEMBER_LIST_PATH), new DeptTreePayload(deptNo, recursive),
                UsersPayload.class).users();
    }

    /**
     * 获取子部门列表。
     *
     * @param deptNo 部门编号
     * @param recursive 是否递归包含全部下级部门
     * @return 子部门列表
     */
    public List<Department> list(long deptNo, boolean recursive) {
        return transport.post(transport.endpoint(LIST_PATH), new DeptTreePayload(deptNo, recursive),
                DepartmentsPayload.class).departments();
    }

    /**
     * 创建部门。
     *
     * @param request 部门信息
     * @return 新建的部门
     */
    public Department create(CreateDepartmentRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(CREATE_PATH), new DepartmentRequestPayload(
                request.deptNo(), request.name(), request.parentNo(), null), DepartmentPayload.class).department();
    }

    /**
     * 修改部门。
     *
     * @param request 修改内容
     * @return 修改后的部门
     */
    public Department update(UpdateDepartmentRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(UPDATE_PATH), new DepartmentRequestPayload(
                        request.deptNo(), request.name(), request.parentNo(), request.seq()),
                DepartmentPayload.class).department();
    }

    /**
     * 删除部门。
     *
     * @param deptNo 部门编号
     */
    public void delete(long deptNo) {
        transport.postVoid(transport.endpoint(DELETE_PATH), new DeptNoPayload(deptNo));
    }

    /**
     * 集成模式下按第三方平台（钉钉、企业微信、飞书）的部门 ID 获取简道云部门。
     *
     * @param integrateId 第三方平台部门 ID
     * @return 部门
     */
    public Department getByIntegrateId(String integrateId) {
        return transport.post(transport.endpoint(DEPT_NO_GET_PATH), new IntegrateIdPayload(
                        JiandaoyunValidationUtils.requireNotBlank(integrateId, "integrateId")),
                DepartmentPayload.class).department();
    }

    /**
     * 全量导入部门（仅公共模式）：以部门编号为主键全量覆盖，单次最多 100000 个，层级最多 16 级，
     * 执行期间会阻塞其他通讯录修改操作。
     *
     * @param departments 全部部门
     */
    public void importDepartments(List<ImportDepartment> departments) {
        transport.postVoid(transport.endpoint(IMPORT_PATH), new ImportPayload(
                List.copyOf(ValidationUtils.requireNonNull(departments, "departments must not be null"))));
    }

    /**
     * 获取部门主管。
     *
     * @param deptNo 部门编号
     * @return 主管列表
     */
    public List<Member> getManagers(long deptNo) {
        return transport.post(transport.endpoint(MANAGER_GET_PATH), new DeptNoPayload(deptNo),
                ManagersPayload.class).deptManagers();
    }

    /**
     * 设置部门主管，覆盖原有主管；传空列表表示清空。
     *
     * @param deptNo 部门编号
     * @param usernames 主管成员编号
     * @return 设置后的主管列表
     */
    public List<Member> setManagers(long deptNo, List<String> usernames) {
        return transport.post(transport.endpoint(MANAGER_UPDATE_PATH), new SetManagersPayload(deptNo,
                        List.copyOf(ValidationUtils.requireNonNull(usernames, "usernames must not be null"))),
                ManagersPayload.class).deptManagers();
    }

    /**
     * 仅含部门编号的请求正文。
     *
     * @param deptNo 部门编号
     */
    private record DeptNoPayload(@JsonProperty("dept_no") long deptNo) {
    }

    /**
     * 部门树查询请求正文。
     *
     * @param deptNo 部门编号
     * @param hasChild 是否递归
     */
    private record DeptTreePayload(
            @JsonProperty("dept_no") long deptNo,
            @JsonProperty("has_child") boolean hasChild
    ) {
    }

    /**
     * 创建或修改部门请求正文；未配置的属性省略。
     *
     * @param deptNo 部门编号
     * @param name 部门名称
     * @param parentNo 父部门编号
     * @param seq 排序
     */
    private record DepartmentRequestPayload(
            @JsonProperty("dept_no") @Nullable Long deptNo,
            @Nullable String name,
            @JsonProperty("parent_no") @Nullable Long parentNo,
            @Nullable Long seq
    ) {
    }

    /**
     * 集成模式部门 ID 请求正文。
     *
     * @param integrateId 第三方平台部门 ID
     */
    private record IntegrateIdPayload(@JsonProperty("integrate_id") String integrateId) {
    }

    /**
     * 全量导入请求正文。
     *
     * @param departments 部门列表
     */
    private record ImportPayload(List<ImportDepartment> departments) {
    }

    /**
     * 设置主管请求正文。
     *
     * @param deptNo 部门编号
     * @param managers 主管成员编号
     */
    private record SetManagersPayload(@JsonProperty("dept_no") long deptNo, List<String> managers) {
    }

    /**
     * 成员列表响应正文。
     *
     * @param users 成员列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UsersPayload(List<Member> users) {
    }

    /**
     * 部门列表响应正文。
     *
     * @param departments 部门列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepartmentsPayload(List<Department> departments) {
    }

    /**
     * 单个部门响应正文。
     *
     * @param department 部门
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepartmentPayload(Department department) {
    }

    /**
     * 主管列表响应正文。
     *
     * @param deptManagers 主管列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ManagersPayload(@JsonProperty("dept_managers") List<Member> deptManagers) {
    }
}
