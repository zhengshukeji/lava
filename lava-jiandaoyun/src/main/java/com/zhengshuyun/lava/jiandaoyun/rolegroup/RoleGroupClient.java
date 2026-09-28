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

package com.zhengshuyun.lava.jiandaoyun.rolegroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 简道云通讯录角色组客户端。集成模式下只能操作自建角色组。
 */
public final class RoleGroupClient {
    /** 列出角色组接口路径。 */
    private static final String LIST_PATH = "/api/v5/corp/role_group/list";
    /** 创建角色组接口路径。 */
    private static final String CREATE_PATH = "/api/v5/corp/role_group/create";
    /** 更新角色组接口路径。 */
    private static final String UPDATE_PATH = "/api/v5/corp/role_group/update";
    /** 删除角色组接口路径。 */
    private static final String DELETE_PATH = "/api/v5/corp/role_group/delete";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建角色组入口。
     *
     * @param transport 共享协议传输层
     */
    public RoleGroupClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 列出角色组。
     *
     * @param request 分页与过滤参数
     * @return 角色组列表
     */
    public List<RoleGroup> list(ListRoleGroupsRequest request) {
        ValidationUtils.requireNonNull(request, "request must not be null");
        return transport.post(transport.endpoint(LIST_PATH), new ListPayload(
                request.skip(), request.limit(), request.hasInternal(), request.hasSync()
        ), RoleGroupsPayload.class).roleGroups();
    }

    /**
     * 创建自建角色组。
     *
     * @param name 角色组名称
     * @return 新建的角色组
     */
    public RoleGroup create(String name) {
        return transport.post(transport.endpoint(CREATE_PATH), new CreatePayload(requireName(name)),
                RoleGroupPayload.class).roleGroup();
    }

    /**
     * 重命名自建角色组。
     *
     * @param groupNo 角色组编号
     * @param name 新名称
     * @return 更新后的角色组
     */
    public RoleGroup update(long groupNo, String name) {
        return transport.post(transport.endpoint(UPDATE_PATH), new UpdatePayload(groupNo, requireName(name)),
                RoleGroupPayload.class).roleGroup();
    }

    /**
     * 删除自建角色组。
     *
     * @param groupNo 角色组编号
     */
    public void delete(long groupNo) {
        transport.postVoid(transport.endpoint(DELETE_PATH), new GroupNoPayload(groupNo));
    }

    /**
     * 校验角色组名称。
     *
     * @param name 名称
     * @return 原值
     */
    private static String requireName(String name) {
        return JiandaoyunValidationUtils.requireNotBlank(name, "name");
    }

    /**
     * 列出角色组请求正文。
     *
     * @param skip 跳过条数
     * @param limit 每页条数
     * @param hasInternal 是否包含自建角色组
     * @param hasSync 是否包含集成同步角色组
     */
    private record ListPayload(
            @Nullable Integer skip,
            @Nullable Integer limit,
            @JsonProperty("has_internal") @Nullable Boolean hasInternal,
            @JsonProperty("has_sync") @Nullable Boolean hasSync
    ) {
    }

    /**
     * 创建角色组请求正文。
     *
     * @param name 名称
     */
    private record CreatePayload(String name) {
    }

    /**
     * 更新角色组请求正文；注意上游字段名为 {@code role_group_no}。
     *
     * @param roleGroupNo 角色组编号
     * @param name 名称
     */
    private record UpdatePayload(@JsonProperty("role_group_no") long roleGroupNo, String name) {
    }

    /**
     * 删除角色组请求正文。
     *
     * @param roleGroupNo 角色组编号
     */
    private record GroupNoPayload(@JsonProperty("role_group_no") long roleGroupNo) {
    }

    /**
     * 角色组列表响应正文。
     *
     * @param roleGroups 角色组列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoleGroupsPayload(@JsonProperty("role_groups") List<RoleGroup> roleGroups) {
    }

    /**
     * 单个角色组响应正文。
     *
     * @param roleGroup 角色组
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoleGroupPayload(@JsonProperty("role_group") RoleGroup roleGroup) {
    }
}
