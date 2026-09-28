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

import com.zhengshuyun.lava.jiandaoyun.role.ListRoleMembersRequest;
import com.zhengshuyun.lava.jiandaoyun.role.ListRolesRequest;
import com.zhengshuyun.lava.jiandaoyun.role.Role;
import com.zhengshuyun.lava.jiandaoyun.role.RoleMember;
import com.zhengshuyun.lava.jiandaoyun.rolegroup.ListRoleGroupsRequest;
import com.zhengshuyun.lava.jiandaoyun.rolegroup.RoleGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云角色与角色组客户端的请求组装与响应解析测试。
 */
class RoleClientTest extends ClientTestSupport {
    /** 单个角色的响应正文。 */
    private static final String ROLE_RESPONSE =
            "{\"role\":{\"role_no\":2558,\"group_no\":2547,\"name\":\"研发中心\",\"type\":0,\"status\":1}}";

    /**
     * 验证角色列表、创建与更新。
     */
    @Test
    void listCreateAndUpdateRoles() {
        server.enqueue(200, "{\"roles\":[{\"role_no\":1,\"group_no\":2,\"name\":\"管理员\"}]}");
        server.enqueue(200, ROLE_RESPONSE);
        server.enqueue(200, ROLE_RESPONSE);

        List<Role> roles = client.roles().list(ListRolesRequest.builder().limit(10).hasSync(false).build());
        Role created = client.roles().create(2547, "研发中心");
        client.roles().update(2558, 2547, null);

        assertRequest("/api/v5/corp/role/list", "{\"limit\":10,\"has_sync\":false}");
        assertRequest("/api/v5/corp/role/create", "{\"group_no\":2547,\"name\":\"研发中心\"}");
        assertRequest("/api/v5/corp/role/update", "{\"role_no\":2558,\"group_no\":2547}");
        assertEquals("管理员", roles.getFirst().name());
        assertEquals(2558L, created.roleNo());
    }

    /**
     * 验证删除角色在成功响应无正文时正常返回。
     */
    @Test
    void deleteAcceptsEmptySuccessBody() {
        server.enqueue(200, "");

        client.roles().delete(2558);

        assertRequest("/api/v5/corp/role/delete", "{\"role_no\":2558}");
    }

    /**
     * 验证角色成员的查询与增减。
     */
    @Test
    void roleMembers() {
        server.enqueue(200, "{\"users\":[{\"username\":\"u1\",\"departments_range\":[1,2],\"has_child\":true}]}");
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, "{\"status\":\"success\"}");

        List<RoleMember> members = client.roles().listMembers(
                ListRoleMembersRequest.builder(3).hasManageRange(true).build());
        client.roles().addMembers(3, List.of("u1"));
        client.roles().removeMembers(3, List.of("u2"));

        assertRequest("/api/v5/corp/role/user/list", "{\"role_no\":3,\"has_manage_range\":true}");
        assertRequest("/api/v5/corp/role/add_members", "{\"role_no\":3,\"usernames\":[\"u1\"]}");
        assertRequest("/api/v5/corp/role/remove_members", "{\"role_no\":3,\"usernames\":[\"u2\"]}");
        assertEquals(List.of(1L, 2L), members.getFirst().departmentsRange());
        assertTrue(members.getFirst().hasChild());
    }

    /**
     * 验证角色组接口使用上游的 role_group_no 字段名。
     */
    @Test
    void roleGroups() {
        server.enqueue(200, "{\"role_groups\":[{\"group_no\":1,\"name\":\"默认\"}]}");
        server.enqueue(200, "{\"role_group\":{\"group_no\":2559,\"name\":\"研发\"}}");
        server.enqueue(200, "{\"role_group\":{\"group_no\":2559,\"name\":\"研发部\"}}");
        server.enqueue(200, "{\"status\":\"success\"}");

        List<RoleGroup> groups = client.roleGroups().list(ListRoleGroupsRequest.builder().build());
        RoleGroup created = client.roleGroups().create("研发");
        RoleGroup updated = client.roleGroups().update(2559, "研发部");
        client.roleGroups().delete(2559);

        assertRequest("/api/v5/corp/role_group/list", "{}");
        assertRequest("/api/v5/corp/role_group/create", "{\"name\":\"研发\"}");
        assertRequest("/api/v5/corp/role_group/update", "{\"role_group_no\":2559,\"name\":\"研发部\"}");
        assertRequest("/api/v5/corp/role_group/delete", "{\"role_group_no\":2559}");
        assertEquals("默认", groups.getFirst().name());
        assertEquals(2559L, created.groupNo());
        assertEquals("研发部", updated.name());
    }

    /**
     * 验证非法参数被拒绝。
     */
    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> client.roles().create(1, " "));
        assertThrows(IllegalArgumentException.class, () -> ListRolesRequest.builder().skip(-1).build());
        assertThrows(IllegalArgumentException.class, () -> client.roleGroups().create(""));
    }
}
