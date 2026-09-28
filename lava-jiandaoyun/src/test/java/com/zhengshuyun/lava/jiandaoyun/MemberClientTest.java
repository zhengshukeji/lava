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

import com.zhengshuyun.lava.jiandaoyun.member.CreateMemberRequest;
import com.zhengshuyun.lava.jiandaoyun.member.ImportMember;
import com.zhengshuyun.lava.jiandaoyun.member.Member;
import com.zhengshuyun.lava.jiandaoyun.member.UpdateMemberRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简道云成员客户端的请求组装与响应解析测试。
 */
class MemberClientTest extends ClientTestSupport {
    /** 单个成员的响应正文。 */
    private static final String MEMBER_RESPONSE = "{\"user\":{\"username\":\"jiandaoyun\",\"name\":\"小云\","
            + "\"departments\":[1,3],\"type\":0,\"status\":1,\"integrate_id\":\"ext-1\"}}";

    /**
     * 验证获取成员的请求与解析。
     */
    @Test
    void getParsesMember() {
        server.enqueue(200, MEMBER_RESPONSE);

        Member member = client.members().get("jiandaoyun");

        assertRequest("/api/v5/corp/user/get", "{\"username\":\"jiandaoyun\"}");
        assertEquals(new Member("jiandaoyun", "小云", List.of(1L, 3L), 0, 1, "ext-1"), member);
    }

    /**
     * 验证添加与修改成员时省略未配置的属性。
     */
    @Test
    void createAndUpdateOmitUnsetFields() {
        server.enqueue(200, MEMBER_RESPONSE);
        server.enqueue(200, MEMBER_RESPONSE);

        client.members().create(CreateMemberRequest.builder().name("小云").departments(1L, 3L).build());
        client.members().update(UpdateMemberRequest.builder().username("jiandaoyun").name("小简").build());

        assertRequest("/api/v5/corp/user/create", "{\"name\":\"小云\",\"departments\":[1,3]}");
        assertRequest("/api/v5/corp/user/update", "{\"username\":\"jiandaoyun\",\"name\":\"小简\"}");
    }

    /**
     * 验证删除、批量删除和增量导入的请求正文。
     */
    @Test
    void deleteBatchDeleteAndImport() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(200, "{\"status\":\"success\"}");
        }

        client.members().delete("u1");
        client.members().batchDelete(List.of("u1", "u2"));
        client.members().importMembers(List.of(new ImportMember("u3", "小简", List.of(1L))));

        assertRequest("/api/v5/corp/user/delete", "{\"username\":\"u1\"}");
        assertRequest("/api/v5/corp/user/batch_delete", "{\"usernames\":[\"u1\",\"u2\"]}");
        assertRequest("/api/v5/corp/user/import",
                "{\"users\":[{\"username\":\"u3\",\"name\":\"小简\",\"departments\":[1]}]}");
    }

    /**
     * 验证缺少必填参数时被拒绝。
     */
    @Test
    void rejectsMissingRequiredArguments() {
        assertThrows(IllegalArgumentException.class, () -> CreateMemberRequest.builder().build());
        assertThrows(IllegalArgumentException.class, () -> UpdateMemberRequest.builder().name("x").build());
        assertThrows(IllegalArgumentException.class, () -> new ImportMember(" ", "x", List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.members().get(""));
    }
}
