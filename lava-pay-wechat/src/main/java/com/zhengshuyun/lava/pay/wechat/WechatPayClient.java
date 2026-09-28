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

package com.zhengshuyun.lava.pay.wechat;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpClient;
import com.zhengshuyun.lava.http.OkHttpInterop;
import com.zhengshuyun.lava.pay.wechat.bill.BillClient;
import com.zhengshuyun.lava.pay.wechat.internal.*;
import com.zhengshuyun.lava.pay.wechat.notification.NotificationParser;
import com.zhengshuyun.lava.pay.wechat.refund.RefundClient;
import com.zhengshuyun.lava.pay.wechat.transaction.TransactionClient;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 线程安全的微信支付 APIv3 普通商户根客户端。
 *
 * <p>根客户端绑定一套商户凭证并共享 HTTP 连接资源。支付产品通过轻量应用上下文使用，
 * 查单、退款、账单和通知等商户级能力直接从根客户端获取。</p>
 */
public final class WechatPayClient implements AutoCloseable {
    /**
     * 微信支付 API 主域名。
     */
    public static final URI DEFAULT_API_BASE_URL = URI.create("https://api.mch.weixin.qq.com/");
    /**
     * 微信支付 API 备用域名。
     */
    public static final URI BACKUP_API_BASE_URL = URI.create("https://api2.mch.weixin.qq.com/");
    /**
     * 默认 HTTP 客户端的空闲连接保留时间。
     *
     * <p>微信支付 API 服务端通过 {@code Keep-Alive: timeout=8} 在连接空闲 8 秒后主动断开，且 HTTP/1.1
     * 断开前不会通知客户端。支付请求必须关闭连接失败重试，复用已断开的连接会直接得到传输失败，
     * 所以本地保留时间必须短于服务端超时，保证过期连接先在本地回收。</p>
     */
    public static final Duration DEFAULT_CONNECTION_KEEP_ALIVE = Duration.ofSeconds(5);

    /**
     * 共享的协议传输层，同时持有 HTTP 资源所有权与客户端关闭状态。
     */
    private final WechatPayTransport transport;
    /**
     * 普通支付交易查单和关单入口。
     */
    private final TransactionClient transactionClient;
    /**
     * 普通支付退款申请和查询入口。
     */
    private final RefundClient refundClient;
    /**
     * 交易账单、资金账单申请及下载入口。
     */
    private final BillClient billClient;
    /**
     * 支付和退款通知验签、解密及解析入口。
     */
    private final NotificationParser notificationParser;

    /**
     * 使用共享传输层创建并缓存各业务入口。
     *
     * @param transport 共享协议传输层
     */
    private WechatPayClient(WechatPayTransport transport) {
        this.transport = transport;
        transactionClient = new TransactionClient(transport);
        refundClient = new RefundClient(transport);
        billClient = new BillClient(transport);
        notificationParser = new NotificationParser(transport);
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
     * 创建绑定 APPID 与支付通知地址的轻量应用上下文。
     *
     * @param appid     已与当前商户号绑定的应用 ID
     * @param notifyUrl 支付结果通知地址
     * @return 可复用应用上下文
     */
    public WechatPayApplication application(String appid, URI notifyUrl) {
        transport.ensureOpen();
        WechatPayValidationUtils.requireAppid(appid);
        WechatPayValidationUtils.requireNotifyUrl(notifyUrl);
        return new WechatPayApplication(transport, appid, notifyUrl);
    }

    /**
     * 使用字符串通知地址创建应用上下文。
     *
     * @param appid     应用 ID
     * @param notifyUrl 支付通知地址
     * @return 可复用应用上下文
     */
    public WechatPayApplication application(String appid, String notifyUrl) {
        return application(appid,
                WechatPayValidationUtils.requireNotifyUrl(notifyUrl));
    }

    /**
     * 返回普通支付交易查单和关单入口。
     *
     * @return 交易客户端
     */
    public TransactionClient transactions() {
        transport.ensureOpen();
        return transactionClient;
    }

    /**
     * 返回普通支付退款入口。
     *
     * @return 退款客户端
     */
    public RefundClient refunds() {
        transport.ensureOpen();
        return refundClient;
    }

    /**
     * 返回交易账单与资金账单入口。
     *
     * @return 账单客户端
     */
    public BillClient bills() {
        transport.ensureOpen();
        return billClient;
    }

    /**
     * 返回框架无关的支付和退款通知解析器。
     *
     * @return 通知解析器
     */
    public NotificationParser notifications() {
        transport.ensureOpen();
        return notificationParser;
    }

    /**
     * 关闭客户端。自建 HTTP 资源会被关闭，调用方传入的 HTTP 客户端保持可用。
     */
    @Override
    public void close() {
        transport.close();
    }

    /**
     * 微信支付普通商户客户端的 fluent 构建器。
     *
     * <p>构建前必须配置商户号、商户私钥、商户证书或证书序列号、APIv3 密钥、微信支付公钥 ID
     * 和微信支付公钥。</p>
     */
    public static final class Builder {

        /**
         * 当前商户号，用于请求签名与业务参数注入。
         */
        private @Nullable String mchid;
        /**
         * 商户 API 私钥，用于构造 APIv3 请求签名。
         */
        private @Nullable PrivateKey merchantPrivateKey;
        /**
         * 可选商户 API 证书，用于提取序列号并校验其与私钥的配对关系。
         */
        private @Nullable X509Certificate merchantCertificate;
        /**
         * 商户 API 证书序列号；未显式配置时由商户证书提取。
         */
        private @Nullable String merchantSerialNo;
        /**
         * APIv3 密钥的防御性副本；构建时传输层会再复制一份，并在客户端关闭时清零。
         */
        private byte @Nullable [] apiV3Key;
        /**
         * 微信支付公钥 ID，用于声明并校验微信支付公钥验签模式。
         */
        private @Nullable String wechatPayPublicKeyId;
        /**
         * 微信支付公钥，用于验证 API 应答和通知签名。
         */
        private @Nullable PublicKey wechatPayPublicKey;
        /**
         * 调用方借出的 HTTP 客户端；未设置时构建器自行创建。
         */
        private @Nullable HttpClient httpClient;
        /**
         * 微信支付 API 根地址，默认使用官方主域名。
         */
        private URI apiBaseUrl = DEFAULT_API_BASE_URL;
        /**
         * 请求签名和响应验签使用的时钟，默认采用 UTC 系统时钟。
         */
        private Clock clock = Clock.systemUTC();
        /**
         * 请求签名随机串生成器，默认使用安全随机实现。
         */
        private Supplier<String> nonceSupplier = WechatPayCryptoUtils::randomNonce;

        /** 创建使用官方主域名和系统时钟的空构建器。 */
        private Builder() {
        }

        /**
         * 配置普通商户号。
         *
         * @param value 商户号
         * @return 当前构建器
         */
        public Builder mchid(String value) {
            mchid = requireHeaderValue(
                    WechatPayValidationUtils.requireMchid(value), "mchid");
            return this;
        }

        /**
         * 从 PKCS#8 PEM 文件配置商户 API 私钥。
         *
         * @param path 私钥文件
         * @return 当前构建器
         */
        public Builder merchantPrivateKey(Path path) {
            return merchantPrivateKey(WechatPayPemUtils.readPrivateKey(path));
        }

        /**
         * 配置来自密钥管理系统或 JCA Provider 的商户 API 私钥。
         *
         * @param value RSA 私钥
         * @return 当前构建器
         */
        public Builder merchantPrivateKey(PrivateKey value) {
            merchantPrivateKey = WechatPayPemUtils.requirePrivateKey(value);
            return this;
        }

        /**
         * 从 PEM 文件配置商户 API 证书，并在构建时自动提取序列号。
         *
         * @param path 证书文件
         * @return 当前构建器
         */
        public Builder merchantCertificate(Path path) {
            return merchantCertificate(WechatPayPemUtils.readCertificate(path));
        }

        /**
         * 配置商户 API X.509 证书，并在构建时自动提取序列号。
         *
         * @param value 商户证书
         * @return 当前构建器
         */
        public Builder merchantCertificate(X509Certificate value) {
            merchantCertificate = WechatPayPemUtils.requireMerchantCertificate(value);
            return this;
        }

        /**
         * 显式配置商户 API 证书序列号，适用于证书不落盘或硬件密钥场景。
         *
         * @param value 大写十六进制序列号
         * @return 当前构建器
         */
        public Builder merchantSerialNo(String value) {
            value = requireHeaderValue(value, "merchantSerialNo");
            ValidationUtils.requireTrue(value.codePoints().allMatch(
                            codePoint -> codePoint >= '0' && codePoint <= '9'
                                    || codePoint >= 'A' && codePoint <= 'F'
                                    || codePoint >= 'a' && codePoint <= 'f'),
                    "merchantSerialNo must contain hexadecimal characters only");
            merchantSerialNo = value.toUpperCase(Locale.ROOT);
            return this;
        }

        /**
         * 以 32 字符文本配置 APIv3 密钥。
         *
         * @param value APIv3 密钥
         * @return 当前构建器
         */
        public Builder apiV3Key(String value) {
            ValidationUtils.requireNonNull(value, "apiV3Key must not be null");
            return apiV3Key(value.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 以 32 字节数组配置 APIv3 密钥。构建器会立即复制输入。
         *
         * @param value 32 字节密钥
         * @return 当前构建器
         */
        public Builder apiV3Key(byte[] value) {
            ValidationUtils.requireNonNull(value, "apiV3Key must not be null");
            // APIv3 密钥直接用作 AES-256-GCM 密钥，必须恰好 32 字节
            ValidationUtils.requireTrue(value.length == 32, "apiV3Key must contain exactly 32 bytes");
            apiV3Key = value.clone();
            return this;
        }

        /**
         * 配置微信支付公钥 ID。
         *
         * @param value 形如 {@code PUB_KEY_ID_数字串} 的 ID
         * @return 当前构建器
         */
        public Builder wechatPayPublicKeyId(String value) {
            wechatPayPublicKeyId = requireHeaderValue(value, "wechatPayPublicKeyId");
            return this;
        }

        /**
         * 从 PEM 文件配置微信支付公钥。
         *
         * @param path 公钥文件
         * @return 当前构建器
         */
        public Builder wechatPayPublicKey(Path path) {
            return wechatPayPublicKey(WechatPayPemUtils.readPublicKey(path));
        }

        /**
         * 配置 JCA 微信支付公钥。
         *
         * @param value RSA 公钥
         * @return 当前构建器
         */
        public Builder wechatPayPublicKey(PublicKey value) {
            wechatPayPublicKey = WechatPayPemUtils.requirePublicKey(value);
            return this;
        }

        /**
         * 借用调用方管理的 HTTP 客户端。关闭微信支付客户端不会关闭该对象。
         * 调用方应关闭该客户端的连接失败重试、普通重定向和跨协议重定向，
         * 并将空闲连接保留时间设为不超过 {@link WechatPayClient#DEFAULT_CONNECTION_KEEP_ALIVE}，否则会复用已被服务端断开的连接。
         *
         * @param value HTTP 客户端
         * @return 当前构建器
         */
        public Builder httpClient(HttpClient value) {
            value = ValidationUtils.requireNonNull(value, "httpClient must not be null");
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
         * 配置微信支付 API 根地址。默认使用主域名，可显式传入 {@link #BACKUP_API_BASE_URL}。
         *
         * <p>生产地址必须使用 HTTPS；为支持本地协议测试，仅允许环回主机使用 HTTP。</p>
         *
         * @param value 不含用户信息、查询参数和片段的绝对 URI
         * @return 当前构建器
         */
        public Builder apiBaseUrl(URI value) {
            apiBaseUrl = WechatPayValidationUtils.requireApiBaseUrl(value);
            return this;
        }

        /**
         * 使用字符串配置微信支付 API 根地址。
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
         * @return 微信支付根客户端
         */
        public WechatPayClient build() {
            // 1. 校验必需配置，避免半初始化客户端进入支付协议流程
            String configuredMchid = ValidationUtils.requireNonNull(mchid, "mchid is required");
            PrivateKey configuredPrivateKey = ValidationUtils.requireNonNull(
                    merchantPrivateKey, "merchantPrivateKey is required");
            String configuredPublicKeyId = ValidationUtils.requireNonNull(
                    wechatPayPublicKeyId, "wechatPayPublicKeyId is required");
            PublicKey configuredPublicKey = ValidationUtils.requireNonNull(
                    wechatPayPublicKey, "wechatPayPublicKey is required");
            byte[] configuredApiV3Key = ValidationUtils.requireNonNull(apiV3Key, "apiV3Key is required");

            // 2. 证书存在时校验其与私钥配对，并统一确定请求签名使用的商户证书序列号
            String certificateSerial = null;
            if (merchantCertificate != null) {
                WechatPayPemUtils.requireKeyPair(configuredPrivateKey, merchantCertificate);
                certificateSerial = WechatPayPemUtils.serialNo(merchantCertificate);
            }
            if (certificateSerial != null && merchantSerialNo != null
                    && !certificateSerial.equalsIgnoreCase(merchantSerialNo)) {
                throw new IllegalArgumentException("merchantSerialNo does not match merchantCertificate");
            }
            String configuredSerial = ValidationUtils.requireNonNull(
                    merchantSerialNo == null ? certificateSerial : merchantSerialNo,
                    "merchantCertificate or merchantSerialNo is required");

            // 3. 未借用外部客户端时创建专属 HTTP 客户端；支付请求不可自动重试或跟随重定向
            boolean ownsClient = httpClient == null;
            HttpClient configuredHttpClient = ownsClient
                    ? HttpClient.builder()
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectionPool(HttpClient.Builder.DEFAULT_MAX_IDLE_CONNECTIONS,
                            DEFAULT_CONNECTION_KEEP_ALIVE)
                    .build()
                    : httpClient;
            try {
                return new WechatPayClient(new WechatPayTransport(
                        configuredMchid,
                        configuredSerial,
                        configuredPrivateKey,
                        configuredPublicKeyId,
                        configuredPublicKey,
                        configuredApiV3Key,
                        configuredHttpClient,
                        ownsClient,
                        apiBaseUrl,
                        clock,
                        nonceSupplier,
                        WechatPayJsonUtils.codec()));
            } catch (RuntimeException exception) {
                // 仅回收本构建器创建的资源，调用方借出的 HTTP 客户端仍由调用方负责关闭
                if (ownsClient) {
                    configuredHttpClient.close();
                }
                throw exception;
            }
        }

        /**
         * 配置协议测试使用的固定时钟。
         *
         * @param value 请求签名和响应时效校验共用的时钟
         * @return 当前构建器
         */
        Builder clock(Clock value) {
            clock = ValidationUtils.requireNonNull(value, "clock must not be null");
            return this;
        }

        /**
         * 配置协议测试使用的随机串供应器。
         *
         * @param value 每次请求的签名随机串供应器
         * @return 当前构建器
         */
        Builder nonceSupplier(Supplier<String> value) {
            nonceSupplier = ValidationUtils.requireNonNull(value,
                    "nonceSupplier must not be null");
            return this;
        }

        /**
         * 校验可安全写入 HTTP 引号参数的文本。
         *
         * @param value 待写入鉴权请求头的文本
         * @param name 用于报错的参数名称
         * @return 校验通过的原文本
         */
        private static String requireHeaderValue(String value, String name) {
            ValidationUtils.requireNotBlank(value, name + " must not be blank");
            ValidationUtils.requireTrue(value.codePoints().noneMatch(
                            codePoint -> codePoint <= 0x20 || codePoint == '"'
                                    || codePoint == '\\' || codePoint == 0x7F),
                    name + " contains a character invalid in an HTTP quoted value");
            return value;
        }

    }
}
