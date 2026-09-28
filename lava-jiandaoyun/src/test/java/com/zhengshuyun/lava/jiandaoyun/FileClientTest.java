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
import com.zhengshuyun.lava.jiandaoyun.file.UploadToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云文件上传客户端的凭证获取、上传请求和失败映射测试。
 */
class FileClientTest extends ClientTestSupport {
    /** 每个用例独立的临时目录。 */
    @TempDir
    Path tempDir;

    /**
     * 验证获取上传凭证的请求正文与响应解析。
     */
    @Test
    void getUploadTokensParsesTokenList() {
        server.enqueue(200, "{\"token_and_url_list\":[{\"url\":\"https://upload.qiniup.com\",\"token\":\"t1\"}]}");

        List<UploadToken> tokens = client.files().getUploadTokens(APP_ID, ENTRY_ID, "tx-1");

        assertEquals(List.of(new UploadToken("https://upload.qiniup.com", "t1")), tokens);
        assertFalse(tokens.getFirst().toString().contains("t1"));
        assertRequest("/api/v5/app/entry/file/get_upload_token", "{\"app_id\":\"" + APP_ID
                + "\",\"entry_id\":\"" + ENTRY_ID + "\",\"transaction_id\":\"tx-1\"}");
    }

    /**
     * 验证上传请求发往凭证地址、不携带 API Key，且 token 字段位于 file 字段之前。
     *
     * @throws IOException 创建临时文件失败时抛出
     */
    @Test
    void uploadSendsMultipartWithoutApiKey() throws IOException {
        Path file = Files.writeString(tempDir.resolve("合同.txt"), "hello-jiandaoyun");
        server.enqueue(200, "{\"token_and_url_list\":[{\"url\":\"" + server.baseUrl() + "upload\",\"token\":\"t1\"}]}");
        server.enqueue(200, "{\"key\":\"file-key-1\"}");

        String key = client.files().upload(APP_ID, ENTRY_ID, "tx-1", file);

        assertEquals("file-key-1", key);
        assertEquals("Bearer test-api-key", server.takeRequest().header("Authorization"));
        JiandaoyunTestServer.CapturedRequest upload = server.takeRequest();
        assertEquals("/upload", upload.target());
        assertNull(upload.header("Authorization"));
        assertTrue(upload.header("Content-Type").startsWith("multipart/form-data"));
        String body = upload.bodyText();
        int tokenIndex = body.indexOf("name=\"token\"");
        int fileIndex = body.indexOf("name=\"file\"");
        assertTrue(tokenIndex >= 0 && fileIndex > tokenIndex);
        assertTrue(body.contains("hello-jiandaoyun"));
    }

    /**
     * 验证对象存储的非标准失败状态码映射为 API 异常。
     *
     * @throws IOException 创建临时文件失败时抛出
     */
    @Test
    void uploadFailureMapsToApiException() throws IOException {
        Path file = Files.writeString(tempDir.resolve("a.txt"), "x");
        server.enqueue(614, "{\"error\":\"file exists\"}");

        JiandaoyunApiException exception = assertThrows(JiandaoyunApiException.class,
                () -> client.files().upload(new UploadToken(server.baseUrl() + "upload", "t1"), file));

        assertEquals(614, exception.statusCode());
        assertEquals(614, exception.code());
        assertEquals("file exists", exception.apiMessage());
    }

    /**
     * 验证非 HTTPS 的非环回上传地址被拒绝，防止文件明文出网。
     *
     * @throws IOException 创建临时文件失败时抛出
     */
    @Test
    void uploadRejectsInsecureUrl() throws IOException {
        Path file = Files.writeString(tempDir.resolve("a.txt"), "x");

        assertThrows(IllegalArgumentException.class,
                () -> client.files().upload(new UploadToken("http://upload.example.com", "t1"), file));
    }
}
