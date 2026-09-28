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
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实例级同步 SMTP 发件器；使用 OAuth2 时持有独立的 HTTP 客户端生命周期。
 *
 * <p>实例关闭后不能复用。调用方不应让 {@link #close()} 与 {@link #send(MailSendRequest)} 并发执行。</p>
 */
public final class MailSender implements AutoCloseable {
    private final SmtpServerConfig config;
    private final MailCredential credential;
    private final MailClientOptions options;
    /**
     * OAuth2 凭证的访问令牌缓存；口令凭证时为 null。
     */
    private final @Nullable OAuth2AccessTokenProvider tokenProvider;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 使用默认客户端选项创建发件器。
     *
     * @param config     SMTP 配置
     * @param credential 认证凭证
     */
    public MailSender(SmtpServerConfig config, MailCredential credential) {
        this(config, credential, MailClientOptions.DEFAULT);
    }

    /**
     * 使用指定客户端选项创建发件器。
     *
     * @param config     SMTP 配置
     * @param credential 认证凭证
     * @param options    客户端选项
     */
    public MailSender(
            SmtpServerConfig config, MailCredential credential, MailClientOptions options) {
        this.config = ValidationUtils.requireNonNull(config, "config");
        this.credential = ValidationUtils.requireNonNull(credential, "credential");
        this.options = ValidationUtils.requireNonNull(options, "options");
        this.tokenProvider = OAuth2AccessTokenProvider.forCredential(credential, options);
    }

    /**
     * 同步构造并提交一封邮件。
     *
     * @param request 发信请求
     * @return SMTP 提交结果
     * @throws MailException 内容超限或 SMTP 操作失败时抛出
     */
    public MailSendResult send(MailSendRequest request) {
        if (closed.get()) {
            throw new IllegalStateException("mail sender is closed");
        }
        ValidationUtils.requireNonNull(request, "request");
        Session session = MailSessionFactory.smtp(config, credential);
        // 先完成纯本地的 MIME 构造与大小校验，避免无效请求触发 OAuth2 网络刷新
        MimeMessage message = MimeMessageFactory.create(
                session, request, options.limits(), options.clock());
        String token = tokenProvider == null ? null : tokenProvider.accessToken();
        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(
                    config.host(), config.port(), credential.username(),
                    MailSessionFactory.authenticationSecret(credential, token));
            Address[] recipients = message.getAllRecipients();
            if (recipients == null || recipients.length == 0) {
                throw new MailException(MailFailureKind.CONFIGURATION, "message has no recipients");
            }
            transport.sendMessage(message, recipients);
            String[] messageIds = message.getHeader("Message-ID");
            return new MailSendResult(
                    messageIds == null || messageIds.length == 0 ? null : messageIds[0],
                    options.clock().instant());
        } catch (MailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw MailFailures.wrap("send SMTP message", exception);
        }
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
}
