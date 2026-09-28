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

package com.zhengshuyun.lava.jiandaoyun;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.OkHttpInterop;
import com.zhengshuyun.lava.jiandaoyun.application.ApplicationClient;
import com.zhengshuyun.lava.jiandaoyun.form.FormClient;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunJsonUtils;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunTransport;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 线程安全的简道云开放 API 根客户端。
 *
 * <p>根客户端绑定一个 API Key 并共享 HTTP 连接资源，Bearer 鉴权、错误映射和 JSON 编解码
 * 集中在内部传输层完成。应用和表单等领域能力从根客户端的对应入口获取。</p>
 */
public final class JiandaoyunClient implements AutoCloseable {
    /**
     * 简道云开放 API 官方域名。
     */
    public static final URI DEFAULT_API_BASE_URL = URI.create("https://api.jiandaoyun.com/");

    /**
     * 共享的协议传输层，同时持有 HTTP 资源所有权与客户端关闭状态。
     */
    private final JiandaoyunTransport transport;
    /**
     * 应用查询入口。
     */
    private final ApplicationClient applicationClient;
    /**
     * 表单和表单字段查询入口。
     */
    private final FormClient formClient;

    /**
     * 使用共享传输层创建并缓存各领域入口。
     *
     * @param transport 共享协议传输层
     */
    private JiandaoyunClient(JiandaoyunTransport transport) {
        this.transport = transport;
        applicationClient = new ApplicationClient(transport);
        formClient = new FormClient(transport);
    }

    /**
     * 创建客户端构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回应用查询入口。
     *
     * @return 应用客户端
     */
    public ApplicationClient applications() {
        transport.ensureOpen();
        return applicationClient;
    }

    /**
     * 返回表单和表单字段查询入口。
     *
     * @return 表单客户端
     */
    public FormClient forms() {
        transport.ensureOpen();
        return formClient;
    }

    /**
     * 关闭客户端。自建 HTTP 资源会被关闭，调用方传入的 HTTP 客户端保持可用。
     */
    @Override
    public void close() {
        transport.close();
    }

    /**
     * 简道云根客户端的 fluent 构建器，构建前必须配置 API Key。
     */
    public static final class Builder {
        /**
         * 简道云开放 API 的 Bearer API Key。
         */
        private @Nullable String apiKey;
        /**
         * 调用方借出的 HTTP 客户端；未设置时构建器自行创建。
         */
        private @Nullable HttpClient httpClient;
        /**
         * 简道云 API 根地址，默认使用官方域名。
         */
        private URI apiBaseUrl = DEFAULT_API_BASE_URL;

        /** 创建使用官方域名的空构建器。 */
        private Builder() {
        }

        /**
         * 配置简道云开放平台的 API Key。
         *
         * @param value 在「开放平台 &gt;&gt; 密钥管理」创建的 API Key
         * @return 当前构建器
         */
        public Builder apiKey(String value) {
            apiKey = ValidationUtils.requireNotBlank(value, "apiKey must not be blank");
            return this;
        }

        /**
         * 借用调用方管理的 HTTP 客户端。关闭简道云客户端不会关闭该对象。
         * 调用方应关闭该客户端的连接失败重试、普通重定向和跨协议重定向。
         *
         * @param value HTTP 客户端
         * @return 当前构建器
         */
        public Builder httpClient(HttpClient value) {
            ValidationUtils.requireNonNull(value, "httpClient must not be null");
            ValidationUtils.requireTrue(
                    !OkHttpInterop.unwrap(value).retryOnConnectionFailure(),
                    "httpClient must disable connection failure retries"
            );
            ValidationUtils.requireTrue(
                    !OkHttpInterop.unwrap(value).followRedirects(),
                    "httpClient must disable redirects"
            );
            ValidationUtils.requireTrue(
                    !OkHttpInterop.unwrap(value).followSslRedirects(),
                    "httpClient must disable cross-protocol redirects"
            );
            httpClient = value;
            return this;
        }

        /**
         * 配置简道云 API 根地址。默认使用官方域名。
         *
         * <p>生产地址必须使用 HTTPS；为支持本地协议测试，仅允许环回主机使用 HTTP。</p>
         *
         * @param value 不含用户信息、查询参数和片段的绝对 URI
         * @return 当前构建器
         */
        public Builder apiBaseUrl(URI value) {
            apiBaseUrl = JiandaoyunValidationUtils.requireApiBaseUrl(value);
            return this;
        }

        /**
         * 使用字符串配置简道云 API 根地址。
         *
         * @param value 根地址
         * @return 当前构建器
         */
        public Builder apiBaseUrl(String value) {
            ValidationUtils.requireNotBlank(value, "apiBaseUrl must not be blank");
            try {
                return apiBaseUrl(new URI(value));
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("apiBaseUrl must be a valid URI", exception);
            }
        }

        /**
         * 创建不可变客户端。构建器可重复使用，每次构建得到独立的客户端。
         *
         * @return 简道云根客户端
         */
        public JiandaoyunClient build() {
            String configuredApiKey = ValidationUtils.requireNonNull(apiKey, "apiKey is required");
            // 未借用外部客户端时创建专属 HTTP 客户端；简道云接口统一使用 POST，
            // 连接失败静默重试可能造成服务端重复计频，必须关闭
            boolean ownsClient = httpClient == null;
            HttpClient configuredHttpClient = ownsClient
                    ? HttpClient.builder()
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                    : httpClient;
            return new JiandaoyunClient(new JiandaoyunTransport(
                    configuredApiKey,
                    configuredHttpClient,
                    ownsClient,
                    apiBaseUrl,
                    JiandaoyunJsonUtils.codec()));
        }
    }
}
