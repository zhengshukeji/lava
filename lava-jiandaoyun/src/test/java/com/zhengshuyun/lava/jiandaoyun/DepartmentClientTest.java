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

import com.zhengshuyun.lava.jiandaoyun.department.CreateDepartmentRequest;
import com.zhengshuyun.lava.jiandaoyun.department.Department;
import com.zhengshuyun.lava.jiandaoyun.department.ImportDepartment;
import com.zhengshuyun.lava.jiandaoyun.department.UpdateDepartmentRequest;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简道云部门与企业互联客户端的请求组装与响应解析测试。
 */
class DepartmentClientTest extends ClientTestSupport {
    /** 单个部门的响应正文。 */
    private static final String DEPARTMENT_RESPONSE =
            "{\"department\":{\"dept_no\":9007199254740991,\"name\":\"研发部\",\"parent_no\":1,\"type\":0,"
                    + "\"status\":1,\"seq\":2}}";

    /**
     * 验证部门列表与部门成员的递归参数和解析，部门编号支持 2^53-1。
     */
    @Test
    void listDepartmentsAndMembers() {
        server.enqueue(200, "{\"departments\":[{\"dept_no\":9007199254740991,\"name\":\"研发部\",\"parent_no\":1}]}");
        server.enqueue(200, "{\"users\":[{\"username\":\"aubrey\",\"name\":\"aubrey\",\"departments\":[1]}]}");

        List<Department> departments = client.departments().list(1, true);
        List<Member> members = client.departments().listMembers(1, false);

        assertRequest("/api/v6/corp/department/list", "{\"dept_no\":1,\"has_child\":true}");
        assertRequest("/api/v5/corp/department/user/list", "{\"dept_no\":1,\"has_child\":false}");
        assertEquals(9007199254740991L, departments.getFirst().deptNo());
        assertEquals(1L, departments.getFirst().parentNo());
        assertEquals("aubrey", members.getFirst().username());
    }

    /**
     * 验证创建、修改、删除和按集成 ID 查询部门的请求正文。
     */
    @Test
    void createUpdateDeleteAndGetByIntegrateId() {
        server.enqueue(200, DEPARTMENT_RESPONSE);
        server.enqueue(200, DEPARTMENT_RESPONSE);
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, DEPARTMENT_RESPONSE);

        Department created = client.departments().create(CreateDepartmentRequest.builder()
                .name("研发部").parentNo(1).build());
        client.departments().update(UpdateDepartmentRequest.builder(5).seq(2).build());
        client.departments().delete(5);
        client.departments().getByIntegrateId("ding-1");

        assertEquals("研发部", created.name());
        assertRequest("/api/v6/corp/department/create", "{\"name\":\"研发部\",\"parent_no\":1}");
        assertRequest("/api/v6/corp/department/update", "{\"dept_no\":5,\"seq\":2}");
        assertRequest("/api/v5/corp/department/delete", "{\"dept_no\":5}");
        assertRequest("/api/v6/corp/department/dept_no/get", "{\"integrate_id\":\"ding-1\"}");
    }

    /**
     * 验证全量导入与部门主管的读写。
     */
    @Test
    void importAndManagers() {
        server.enqueue(200, "{\"status\":\"success\"}");
        server.enqueue(200, "{\"dept_managers\":[{\"username\":\"boss\"}]}");
        server.enqueue(200, "{\"dept_managers\":[]}");

        client.departments().importDepartments(List.of(
                new ImportDepartment(2, "销售部", null), new ImportDepartment(3, "华东", 2L)));
        List<Member> managers = client.departments().getManagers(2);
        List<Member> cleared = client.departments().setManagers(2, List.of());

        assertRequest("/api/v5/corp/department/import", "{\"departments\":["
                + "{\"dept_no\":2,\"name\":\"销售部\"},{\"dept_no\":3,\"name\":\"华东\",\"parent_no\":2}]}");
        assertRequest("/api/v6/corp/department/manager/get", "{\"dept_no\":2}");
        assertRequest("/api/v6/corp/department/manager/update", "{\"dept_no\":2,\"managers\":[]}");
        assertEquals("boss", managers.getFirst().username());
        assertEquals(List.of(), cleared);
    }

    /**
     * 验证企业互联查询：未指定部门时省略 dept_no。
     */
    @Test
    void guestQueries() {
        server.enqueue(200, "{\"dept_list\":[{\"name\":\"合作方\",\"dept_no\":1012,\"type\":2,\"status\":1}]}");
        server.enqueue(200, "{\"member_list\":[{\"name\":\"Purl\",\"username\":\"R-1\",\"departments\":[19]}]}");
        server.enqueue(200, "{\"member\":{\"name\":\"Purl\",\"username\":\"R-1\",\"type\":2}}");

        List<Department> departments = client.guests().listDepartments();
        List<Member> members = client.guests().listMembers(19);
        Member member = client.guests().getMember("R-1");

        assertRequest("/api/v5/corp/guest/department/list", "{}");
        assertRequest("/api/v5/corp/guest/user/list", "{\"dept_no\":19}");
        assertRequest("/api/v5/corp/guest/user/get", "{\"username\":\"R-1\"}");
        assertEquals(1012L, departments.getFirst().deptNo());
        assertNull(departments.getFirst().parentNo());
        assertEquals("R-1", members.getFirst().username());
        assertEquals(2, member.type());
    }

    /**
     * 验证缺少必填参数时被拒绝。
     */
    @Test
    void rejectsMissingRequiredArguments() {
        assertThrows(IllegalArgumentException.class, () -> CreateDepartmentRequest.builder().build());
        assertThrows(IllegalArgumentException.class, () -> UpdateDepartmentRequest.builder(5).build());
        assertThrows(IllegalArgumentException.class, () -> client.departments().getByIntegrateId(" "));
    }
}
