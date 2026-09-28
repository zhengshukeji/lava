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

package com.zhengshuyun.lava.jiandaoyun.role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云通讯录角色客户端。集成模式下只能操作自建角色，不能操作第三方同步的角色。
 */
public final class RoleClient {
    /** 列出角色接口路径。 */
    private static final String LIST_PATH = "/api/v5/corp/role/list";
    /** 创建角色接口路径。 */
    private static final String CREATE_PATH = "/api/v5/corp/role/create";
    /** 更新角色接口路径。 */
    private static final String UPDATE_PATH = "/api/v5/corp/role/update";
    /** 删除角色接口路径。 */
    private static final String DELETE_PATH = "/api/v5/corp/role/delete";
    /** 列出角色成员接口路径。 */
    private static final String MEMBER_LIST_PATH = "/api/v5/corp/role/user/list";
    /** 批量添加角色成员接口路径。 */
    private static final String ADD_MEMBERS_PATH = "/api/v5/corp/role/add_members";
    /** 批量移除角色成员接口路径。 */
    private static final String REMOVE_MEMBERS_PATH = "/api/v5/corp/role/remove_members";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建角色入口。
     *
     * @param transport 共享协议传输层
     */
    public RoleClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 列出角色。
     *
     * @param request 分页与过滤参数
     * @return 角色列表
     */
    public List<Role> list(ListRolesRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(LIST_PATH), new ListPayload(
                request.skip(), request.limit(), request.hasInternal(), request.hasSync()
        ), RolesPayload.class).roles();
    }

    /**
     * 创建自建角色。
     *
     * @param groupNo 所属角色组编号
     * @param name 角色名称
     * @return 新建的角色
     */
    public Role create(long groupNo, String name) {
        return transport.post(transport.endpoint(CREATE_PATH), new RoleRequestPayload(
                null, groupNo, JiandaoyunValidationUtils.requireNotBlank(name, "name")
        ), RolePayload.class).role();
    }

    /**
     * 更新自建角色的名称或所属角色组。
     *
     * @param roleNo 角色编号
     * @param groupNo 所属角色组编号
     * @param name 新名称；为 {@code null} 时不修改
     * @return 更新后的角色
     */
    public Role update(long roleNo, long groupNo, @Nullable String name) {
        return transport.post(transport.endpoint(UPDATE_PATH), new RoleRequestPayload(roleNo, groupNo, name),
                RolePayload.class).role();
    }

    /**
     * 删除自建角色。
     *
     * @param roleNo 角色编号
     */
    public void delete(long roleNo) {
        transport.postVoid(transport.endpoint(DELETE_PATH), new RoleNoPayload(roleNo));
    }

    /**
     * 列出角色下的成员。
     *
     * @param request 角色与分页参数
     * @return 成员列表
     */
    public List<RoleMember> listMembers(ListRoleMembersRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(MEMBER_LIST_PATH), new MemberListPayload(
                request.roleNo(), request.skip(), request.limit(), request.hasManageRange()
        ), UsersPayload.class).users();
    }

    /**
     * 为自建角色批量添加成员。
     *
     * @param roleNo 角色编号
     * @param usernames 成员编号
     */
    public void addMembers(long roleNo, List<String> usernames) {
        transport.postVoid(transport.endpoint(ADD_MEMBERS_PATH), new MembersPayload(roleNo, copy(usernames)));
    }

    /**
     * 为自建角色批量移除成员。
     *
     * @param roleNo 角色编号
     * @param usernames 成员编号
     */
    public void removeMembers(long roleNo, List<String> usernames) {
        transport.postVoid(transport.endpoint(REMOVE_MEMBERS_PATH), new MembersPayload(roleNo, copy(usernames)));
    }

    /**
     * 复制成员编号列表。
     *
     * @param usernames 成员编号
     * @return 不可变副本
     */
    private static List<String> copy(List<String> usernames) {
        return List.copyOf(ValidationUtils.requireNonNull(usernames, "usernames must not be null"));
    }

    /**
     * 列出角色请求正文。
     *
     * @param skip 跳过条数
     * @param limit 每页条数
     * @param hasInternal 是否包含自建角色
     * @param hasSync 是否包含集成同步角色
     */
    private record ListPayload(
            @Nullable Integer skip,
            @Nullable Integer limit,
            @JsonProperty("has_internal") @Nullable Boolean hasInternal,
            @JsonProperty("has_sync") @Nullable Boolean hasSync
    ) {
    }

    /**
     * 创建或更新角色请求正文。
     *
     * @param roleNo 角色编号；创建时为 {@code null}
     * @param groupNo 角色组编号
     * @param name 角色名称
     */
    private record RoleRequestPayload(
            @JsonProperty("role_no") @Nullable Long roleNo,
            @JsonProperty("group_no") long groupNo,
            @Nullable String name
    ) {
    }

    /**
     * 仅含角色编号的请求正文。
     *
     * @param roleNo 角色编号
     */
    private record RoleNoPayload(@JsonProperty("role_no") long roleNo) {
    }

    /**
     * 角色成员查询请求正文。
     *
     * @param roleNo 角色编号
     * @param skip 跳过条数
     * @param limit 每页条数
     * @param hasManageRange 是否返回分管范围
     */
    private record MemberListPayload(
            @JsonProperty("role_no") long roleNo,
            @Nullable Integer skip,
            @Nullable Integer limit,
            @JsonProperty("has_manage_range") @Nullable Boolean hasManageRange
    ) {
    }

    /**
     * 角色成员增减请求正文。
     *
     * @param roleNo 角色编号
     * @param usernames 成员编号
     */
    private record MembersPayload(@JsonProperty("role_no") long roleNo, List<String> usernames) {
    }

    /**
     * 角色列表响应正文。
     *
     * @param roles 角色列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RolesPayload(List<Role> roles) {
    }

    /**
     * 单个角色响应正文。
     *
     * @param role 角色
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RolePayload(Role role) {
    }

    /**
     * 角色成员响应正文。
     *
     * @param users 成员列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UsersPayload(List<RoleMember> users) {
    }
}
