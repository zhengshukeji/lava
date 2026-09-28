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
package com.zhengshuyun.lava.mail;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 UID 的同步 IMAP 收件器，每次操作都会独立打开并关闭邮箱连接。
 *
 * <p>实例关闭后不能复用。调用方不应让 {@link #close()} 与其他操作并发执行。</p>
 */
public final class MailReader implements AutoCloseable {
    private final ImapServerConfig config;
    private final MailCredential credential;
    private final MailLimits limits;
    private final ImapMailReader reader = new ImapMailReader();
    /**
     * OAuth2 凭证的访问令牌缓存；口令凭证时为 null。
     */
    private final @Nullable OAuth2AccessTokenProvider tokenProvider;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 使用默认客户端选项创建收件器。
     *
     * @param config     IMAP 配置
     * @param credential 认证凭证
     */
    public MailReader(ImapServerConfig config, MailCredential credential) {
        this(config, credential, MailClientOptions.DEFAULT);
    }

    /**
     * 使用指定客户端选项创建收件器。
     *
     * @param config     IMAP 配置
     * @param credential 认证凭证
     * @param options    客户端选项
     */
    public MailReader(
            ImapServerConfig config, MailCredential credential, MailClientOptions options) {
        this.config = ValidationUtils.requireNonNull(config, "config");
        this.credential = ValidationUtils.requireNonNull(credential, "credential");
        ValidationUtils.requireNonNull(options, "options");
        this.limits = options.limits();
        this.tokenProvider = OAuth2AccessTokenProvider.forCredential(credential, options);
    }

    /**
     * 按 UID 降序列出一页消息摘要。
     *
     * @param query 查询条件
     * @return 消息摘要页
     * @throws MailException 连接、认证、协议或消息解析失败时抛出
     */
    public MailPage<MailMessageSummary> listMessages(MailQuery query) {
        ensureOpen();
        return reader.list(config, credential, accessToken(),
                ValidationUtils.requireNonNull(query, "query"), limits);
    }

    /**
     * 读取指定消息的摘要与受限正文。
     *
     * @param id 消息标识
     * @return 邮件消息
     * @throws MailException 消息不存在、UIDVALIDITY 变化或读取失败时抛出
     */
    public MailMessage readMessage(MailMessageId id) {
        ensureOpen();
        return reader.read(config, credential, accessToken(),
                ValidationUtils.requireNonNull(id, "id"), limits);
    }

    /**
     * 将已解码附件字节写入借用的目标流，不会关闭或刷新 {@code destination}。
     * 如果解码或大小限制失败，目标流中可能已经包含一段受限前缀。
     *
     * @param id              消息标识
     * @param attachmentIndex 摘要中附件元数据的索引
     * @param destination     调用方持有的目标流
     * @return 写入的解码字节数
     * @throws MailException 消息、附件不存在或下载失败时抛出
     */
    public long downloadAttachment(
            MailMessageId id, int attachmentIndex, OutputStream destination) {
        ensureOpen();
        if (attachmentIndex < 0) {
            throw new IllegalArgumentException("attachmentIndex must not be negative");
        }
        return reader.download(
                config, credential, accessToken(), ValidationUtils.requireNonNull(id, "id"),
                attachmentIndex, ValidationUtils.requireNonNull(destination, "destination"), limits);
    }

    /**
     * 关闭实例持有的 OAuth2 HTTP 资源；可重复调用。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && tokenProvider != null) {
            tokenProvider.close();
        }
    }

    private @Nullable String accessToken() {
        return tokenProvider == null ? null : tokenProvider.accessToken();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("mail reader is closed");
        }
    }
}
