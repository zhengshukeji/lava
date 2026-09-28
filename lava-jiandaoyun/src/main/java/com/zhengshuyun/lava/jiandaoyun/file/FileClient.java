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

package com.zhengshuyun.lava.jiandaoyun.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpMediaTypes;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 简道云文件上传客户端。
 *
 * <p>附件和图片字段的写入分三步：用事务 ID 获取上传凭证、上传文件得到 key、新建或修改数据时把 key
 * 写入字段并传入同一个事务 ID。事务 ID 由调用方生成（建议 UUID），1 小时内有效：</p>
 * <pre>{@code
 * String transactionId = UUID.randomUUID().toString();
 * String key = client.files().upload(appId, entryId, transactionId, Path.of("合同.pdf"));
 * client.data().create(CreateDataRequest.builder()
 *         .appId(appId).entryId(entryId)
 *         .data(Map.of("_widget_attachment", List.of(key)))
 *         .transactionId(transactionId)
 *         .build());
 * }</pre>
 */
public final class FileClient {
    /** 获取文件上传凭证接口路径。 */
    private static final String UPLOAD_TOKEN_PATH = "/api/v5/app/entry/file/get_upload_token";

    /** 根客户端共享的鉴权传输层与关闭状态。 */
    private final JiandaoyunTransport transport;

    /**
     * 由根客户端创建文件入口。
     *
     * @param transport 共享协议传输层
     */
    public FileClient(JiandaoyunTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 获取一批上传凭证（每次最多返回 100 个），每个凭证只能上传一个文件。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param transactionId 事务 ID，后续写入数据时须使用同一个
     * @return 上传凭证列表
     */
    public List<UploadToken> getUploadTokens(String appId, String entryId, String transactionId) {
        return transport.post(transport.endpoint(UPLOAD_TOKEN_PATH), new UploadTokenRequestPayload(
                JiandaoyunValidationUtils.requireAppId(appId),
                JiandaoyunValidationUtils.requireEntryId(entryId),
                JiandaoyunValidationUtils.requireNotBlank(transactionId, "transactionId")
        ), UploadTokenPayload.class).tokenAndUrlList();
    }

    /**
     * 使用上传凭证上传文件，媒体类型按文件名推断，无法推断时为 application/octet-stream。
     *
     * @param token 上传凭证
     * @param file 本地文件
     * @return 文件 key，写入附件或图片字段
     */
    public String upload(UploadToken token, Path file) {
        return upload(token, file, probeContentType(file));
    }

    /**
     * 使用上传凭证上传文件。
     *
     * @param token 上传凭证
     * @param file 本地文件
     * @param contentType 文件媒体类型，例如 application/pdf
     * @return 文件 key，写入附件或图片字段
     */
    public String upload(UploadToken token, Path file, String contentType) {
        ValidationUtils.requireNonNull(token, "token must not be null");
        ValidationUtils.requireNonNull(file, "file must not be null");
        URI url = JiandaoyunValidationUtils.requireUploadUrl(URI.create(token.url()));
        return transport.upload(url, token.token(), file,
                JiandaoyunValidationUtils.requireNotBlank(contentType, "contentType"));
    }

    /**
     * 获取一个上传凭证并上传文件。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param transactionId 事务 ID，后续写入数据时须使用同一个
     * @param file 本地文件
     * @return 文件 key，写入附件或图片字段
     */
    public String upload(String appId, String entryId, String transactionId, Path file) {
        ValidationUtils.requireNonNull(file, "file must not be null");
        List<UploadToken> tokens = getUploadTokens(appId, entryId, transactionId);
        if (tokens.isEmpty()) {
            throw new JiandaoyunProtocolException("简道云未返回上传凭证");
        }
        return upload(tokens.getFirst(), file);
    }

    /**
     * 按文件名推断媒体类型。
     *
     * @param file 本地文件
     * @return 媒体类型
     */
    private static String probeContentType(Path file) {
        ValidationUtils.requireNonNull(file, "file must not be null");
        try {
            String contentType = Files.probeContentType(file);
            return contentType == null ? HttpMediaTypes.APPLICATION_OCTET_STREAM : contentType;
        } catch (IOException exception) {
            throw new UncheckedIOException("无法识别文件类型", exception);
        }
    }

    /**
     * 获取上传凭证请求正文。
     *
     * @param appId 应用 ID
     * @param entryId 表单 ID
     * @param transactionId 事务 ID
     */
    private record UploadTokenRequestPayload(
            @JsonProperty("app_id") String appId,
            @JsonProperty("entry_id") String entryId,
            @JsonProperty("transaction_id") String transactionId
    ) {
    }

    /**
     * 获取上传凭证响应正文。
     *
     * @param tokenAndUrlList 上传凭证列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UploadTokenPayload(@JsonProperty("token_and_url_list") List<UploadToken> tokenAndUrlList) {
    }
}
