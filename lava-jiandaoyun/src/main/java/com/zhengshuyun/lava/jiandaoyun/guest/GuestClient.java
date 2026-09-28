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

package com.zhengshuyun.lava.jiandaoyun.guest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.department.Department;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云企业互联客户端：查询已连接的外部企业及其对接人。只返回已加入的连接。
 */
public final class GuestClient {
    /** 列出连接企业接口路径。 */
    private static final String DEPARTMENT_LIST_PATH = "/api/v5/corp/guest/department/list";
    /** 列出对接人接口路径。 */
    private static final String MEMBER_LIST_PATH = "/api/v5/corp/guest/user/list";
    /** 获取对接人详情接口路径。 */
    private static final String MEMBER_GET_PATH = "/api/v5/corp/guest/user/get";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建企业互联入口。
     *
     * @param transport 共享协议传输层
     */
    public GuestClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 列出全部连接企业（外部部门）。
     *
     * @return 外部部门列表
     */
    public List<Department> listDepartments() {
        return queryDepartments(null);
    }

    /**
     * 列出指定外部部门下的连接企业。
     *
     * @param deptNo 外部部门编号
     * @return 外部部门列表
     */
    public List<Department> listDepartments(long deptNo) {
        return queryDepartments(deptNo);
    }

    /**
     * 列出全部对接人。
     *
     * @return 对接人列表
     */
    public List<Member> listMembers() {
        return queryMembers(null);
    }

    /**
     * 列出指定外部部门下的对接人。
     *
     * @param deptNo 外部部门编号
     * @return 对接人列表
     */
    public List<Member> listMembers(long deptNo) {
        return queryMembers(deptNo);
    }

    /**
     * 获取对接人详情。
     *
     * @param username 对接人成员编号
     * @return 对接人
     */
    public Member getMember(String username) {
        return transport.post(transport.endpoint(MEMBER_GET_PATH), new UsernamePayload(
                JiandaoyunValidationUtils.requireNotBlank(username, "username")
        ), MemberPayload.class).member();
    }

    /**
     * 发送连接企业查询。
     *
     * @param deptNo 外部部门编号；为 {@code null} 时查询全部
     * @return 外部部门列表
     */
    private List<Department> queryDepartments(@Nullable Long deptNo) {
        return transport.post(transport.endpoint(DEPARTMENT_LIST_PATH), new DeptNoPayload(deptNo),
                DepartmentsPayload.class).deptList();
    }

    /**
     * 发送对接人查询。
     *
     * @param deptNo 外部部门编号；为 {@code null} 时查询全部
     * @return 对接人列表
     */
    private List<Member> queryMembers(@Nullable Long deptNo) {
        return transport.post(transport.endpoint(MEMBER_LIST_PATH), new DeptNoPayload(deptNo),
                MembersPayload.class).memberList();
    }

    /**
     * 可选部门编号请求正文。
     *
     * @param deptNo 外部部门编号
     */
    private record DeptNoPayload(@JsonProperty("dept_no") @Nullable Long deptNo) {
    }

    /**
     * 成员编号请求正文。
     *
     * @param username 成员编号
     */
    private record UsernamePayload(String username) {
    }

    /**
     * 连接企业响应正文。
     *
     * @param deptList 外部部门列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepartmentsPayload(@JsonProperty("dept_list") List<Department> deptList) {
    }

    /**
     * 对接人列表响应正文。
     *
     * @param memberList 对接人列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MembersPayload(@JsonProperty("member_list") List<Member> memberList) {
    }

    /**
     * 对接人详情响应正文。
     *
     * @param member 对接人
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemberPayload(Member member) {
    }
}
