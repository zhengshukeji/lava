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
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunRuntime;
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
     * 集中管理共享传输层、HTTP 资源所有权和客户端关闭状态的运行时。
     */
    private final JiandaoyunRuntime runtime;
    /**
     * 应用查询入口。
     */
    private final ApplicationClient applicationClient;
    /**
     * 表单和表单字段查询入口。
     */
    private final FormClient formClient;

    /**
     * 使用共享运行时创建并缓存各领域入口。
     *
     * @param runtime 已建立传输层与 HTTP 资源所有权的共享运行时
     */
    private JiandaoyunClient(JiandaoyunRuntime runtime) {
        this.runtime = runtime;
        applicationClient = new ApplicationClient(runtime);
        formClient = new FormClient(runtime);
    }

    /**
     * 创建一次性客户端构建器。
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
        runtime.ensureOpen();
        return applicationClient;
    }

    /**
     * 返回表单和表单字段查询入口。
     *
     * @return 表单客户端
     */
    public FormClient forms() {
        runtime.ensureOpen();
        return formClient;
    }

    /**
     * 关闭客户端并清除 API Key 引用。自建 HTTP 资源会被关闭，
     * 调用方传入的 HTTP 客户端保持可用。
     */
    @Override
    public void close() {
        runtime.close();
    }

    /**
     * 简道云根客户端的一次性 fluent 构建器。
     *
     * <p>构建前必须配置 API Key。每次构建尝试后，构建器都会清除 API Key 引用；失败后重试
     * 必须重新配置。构建成功后，所有配置方法和 {@link #build()} 均不可再次调用。</p>
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
        /**
         * 构建成功标记，防止构建器重复持有或使用敏感配置。
         */
        private boolean built;

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
            ensureNotBuilt();
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
            ensureNotBuilt();
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
            ensureNotBuilt();
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
            ensureNotBuilt();
            ValidationUtils.requireNotBlank(value, "apiBaseUrl must not be blank");
            try {
                return apiBaseUrl(new URI(value));
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("apiBaseUrl must be a valid URI");
            }
        }

        /**
         * 创建不可变客户端。构建器只能成功构建一次，每次尝试后都会释放 API Key 引用。
         *
         * @return 简道云根客户端
         */
        public JiandaoyunClient build() {
            ensureNotBuilt();
            try {
                // 1. 一次性读取并校验必需配置，避免半初始化客户端进入简道云协议流程。
                String configuredApiKey = ValidationUtils.requireNonNull(
                        apiKey,
                        "apiKey is required"
                );

                // 2. 未借用外部客户端时创建专属 HTTP 客户端；简道云接口统一使用 POST，
                //    连接失败静默重试可能造成服务端重复计频，必须关闭。
                HttpClient configuredHttpClient = httpClient;
                boolean ownsClient = configuredHttpClient == null;
                if (configuredHttpClient == null) {
                    configuredHttpClient = HttpClient.builder()
                            .retryOnConnectionFailure(false)
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build();
                }

                try {
                    // 3. 封装协议能力与资源所有权，再创建只负责暴露领域入口的根客户端。
                    JiandaoyunTransport transport = new JiandaoyunTransport(
                            configuredApiKey,
                            configuredHttpClient,
                            apiBaseUrl,
                            JiandaoyunJsonUtils.codec()
                    );
                    JiandaoyunRuntime runtime = new JiandaoyunRuntime(
                            transport,
                            configuredHttpClient,
                            ownsClient
                    );
                    JiandaoyunClient client = new JiandaoyunClient(runtime);
                    built = true;
                    return client;
                } catch (RuntimeException exception) {
                    // 仅回收本构建器创建的资源，调用方借出的 HTTP 客户端仍由调用方负责关闭。
                    if (ownsClient) {
                        configuredHttpClient.close();
                    }
                    throw exception;
                }
            } finally {
                // 4. 每次构建尝试后都清除 API Key 引用，失败重试必须重新配置。
                apiKey = null;
            }
        }

        /**
         * 确认构建器尚未成功创建客户端。
         *
         * @throws IllegalStateException 构建器已经成功使用
         */
        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("JiandaoyunClient.Builder cannot be reused");
            }
        }
    }
}
