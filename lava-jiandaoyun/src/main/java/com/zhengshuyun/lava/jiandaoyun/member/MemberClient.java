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

package com.zhengshuyun.lava.jiandaoyun.member;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云通讯录成员客户端。
 */
public final class MemberClient {
    /** 获取成员接口路径。 */
    private static final String GET_PATH = "/api/v5/corp/user/get";
    /** 添加成员接口路径。 */
    private static final String CREATE_PATH = "/api/v5/corp/user/create";
    /** 修改成员接口路径。 */
    private static final String UPDATE_PATH = "/api/v5/corp/user/update";
    /** 删除成员接口路径。 */
    private static final String DELETE_PATH = "/api/v5/corp/user/delete";
    /** 批量删除成员接口路径。 */
    private static final String BATCH_DELETE_PATH = "/api/v5/corp/user/batch_delete";
    /** 增量导入成员接口路径。 */
    private static final String IMPORT_PATH = "/api/v5/corp/user/import";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建成员入口。
     *
     * @param transport 共享协议传输层
     */
    public MemberClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 获取成员信息。
     *
     * @param username 成员编号
     * @return 成员
     */
    public Member get(String username) {
        return transport.post(transport.endpoint(GET_PATH), new UsernamePayload(requireUsername(username)),
                MemberPayload.class).user();
    }

    /**
     * 添加成员。
     *
     * @param request 成员信息
     * @return 新建的成员
     */
    public Member create(CreateMemberRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(CREATE_PATH), new MemberRequestPayload(
                request.username(), request.name(), request.departments()), MemberPayload.class).user();
    }

    /**
     * 修改成员。
     *
     * @param request 修改内容
     * @return 修改后的成员
     */
    public Member update(UpdateMemberRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(UPDATE_PATH), new MemberRequestPayload(
                request.username(), request.name(), request.departments()), MemberPayload.class).user();
    }

    /**
     * 删除成员。删除即把在职成员转为离职，并非彻底删除。
     *
     * @param username 成员编号
     */
    public void delete(String username) {
        transport.postVoid(transport.endpoint(DELETE_PATH), new UsernamePayload(requireUsername(username)));
    }

    /**
     * 批量删除成员（仅公共模式）。
     *
     * @param usernames 成员编号列表
     */
    public void batchDelete(List<String> usernames) {
        transport.postVoid(transport.endpoint(BATCH_DELETE_PATH), new UsernamesPayload(
                List.copyOf(ValidationUtils.requireNonNull(usernames, "usernames must not be null"))));
    }

    /**
     * 增量导入成员：按成员编号新建或更新，不删除未出现的成员。单次最多 20000 人，
     * 执行期间会阻塞其他通讯录修改操作。
     *
     * @param members 待导入的成员
     */
    public void importMembers(List<ImportMember> members) {
        transport.postVoid(transport.endpoint(IMPORT_PATH), new ImportPayload(
                List.copyOf(ValidationUtils.requireNonNull(members, "members must not be null"))));
    }

    /**
     * 校验成员编号。
     *
     * @param username 成员编号
     * @return 原值
     */
    private static String requireUsername(String username) {
        return JiandaoyunValidationUtils.requireNotBlank(username, "username");
    }

    /**
     * 仅含成员编号的请求正文。
     *
     * @param username 成员编号
     */
    private record UsernamePayload(String username) {
    }

    /**
     * 成员编号列表请求正文。
     *
     * @param usernames 成员编号列表
     */
    private record UsernamesPayload(List<String> usernames) {
    }

    /**
     * 添加或修改成员的请求正文；未配置的属性省略。
     *
     * @param username 成员编号
     * @param name 昵称
     * @param departments 所属部门编号
     */
    private record MemberRequestPayload(
            @Nullable String username,
            @Nullable String name,
            @Nullable List<Long> departments
    ) {
    }

    /**
     * 增量导入请求正文。
     *
     * @param users 成员列表
     */
    private record ImportPayload(List<ImportMember> users) {
    }

    /**
     * 单个成员响应正文。
     *
     * @param user 成员
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemberPayload(Member user) {
    }
}
