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

package com.zhengshuyun.lava.pay.wechat.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhengshuyun.lava.http.*;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import com.zhengshuyun.lava.pay.wechat.exception.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 微信支付 APIv3 的统一签名、发送、验签和错误解析传输层。
 *
 * <p>该类型仅由根客户端及各产品入口共享使用，集中保证请求正文签名与发送一致、所有业务响应
 * 先验签后解析，并将底层 HTTP 失败转换为微信支付领域异常。传输层同时持有根客户端的关闭状态，
 * 关闭时只关闭自己创建的 HTTP 客户端。</p>
 */
public final class WechatPayTransport implements AutoCloseable {
    /** 无请求正文时参与签名和发送的共享空字节数组。 */
    private static final byte[] EMPTY_BODY = new byte[0];
    /** 下载账单失败响应允许读取的最大字节数，防止错误正文无限占用内存。 */
    private static final int MAX_DOWNLOAD_ERROR_BYTES = 64 * 1024;

    /** 当前普通商户号，写入请求签名和需携带商户号的业务参数。 */
    private final String mchid;
    /** 用于请求签名的商户 API 证书序列号。 */
    private final String merchantSerialNo;
    /** 用于生成 APIv3 请求签名的商户 API 私钥。 */
    private final PrivateKey merchantPrivateKey;
    /** 当前使用的微信支付公钥 ID，用于声明并匹配响应或通知签名。 */
    private final String wechatPayPublicKeyId;
    /** 用于验签微信支付 API 应答和通知的微信支付公钥。 */
    private final PublicKey wechatPayPublicKey;
    /** APIv3 密钥的内部副本，仅用于解密回调通知资源。 */
    private final byte[] apiV3Key;
    /** 发送请求的 HTTP 客户端。 */
    private final HttpClient httpClient;
    /** 是否由本传输层负责关闭 HTTP 客户端；调用方借入的客户端为 false。 */
    private final boolean ownsHttpClient;
    /** 根客户端关闭状态，保证关闭幂等并拒绝后续调用。 */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** 已校验的微信支付 API 根地址，用于构造业务接口端点。 */
    private final URI apiBaseUrl;
    /** 请求签名与消息时效校验共用的时钟。 */
    private final Clock clock;
    /** 为每次请求签名生成随机串的供应器。 */
    private final Supplier<String> nonceSupplier;
    /** 业务请求编码及已验签响应解码使用的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;

    /**
     * 创建内部传输层；参数已由根客户端构建器校验。
     *
     * @param mchid 商户号
     * @param merchantSerialNo 商户 API 证书序列号
     * @param merchantPrivateKey 商户私钥
     * @param wechatPayPublicKeyId 微信支付公钥 ID
     * @param wechatPayPublicKey 微信支付公钥
     * @param apiV3Key APIv3 密钥
     * @param httpClient HTTP 客户端
     * @param ownsHttpClient 是否由本传输层负责关闭 HTTP 客户端
     * @param apiBaseUrl API 根地址
     * @param clock 签名和验签时钟
     * @param nonceSupplier 请求随机串生成器
     * @param jsonCodec JSON 编解码器
     */
    public WechatPayTransport(
            String mchid,
            String merchantSerialNo,
            PrivateKey merchantPrivateKey,
            String wechatPayPublicKeyId,
            PublicKey wechatPayPublicKey,
            byte[] apiV3Key,
            HttpClient httpClient,
            boolean ownsHttpClient,
            URI apiBaseUrl,
            Clock clock,
            Supplier<String> nonceSupplier,
            JsonCodec jsonCodec
    ) {
        // 参数已由根客户端构建器校验；APIv3 密钥复制一份，关闭时清零不影响构建器
        this.mchid = mchid;
        this.merchantSerialNo = merchantSerialNo;
        this.merchantPrivateKey = merchantPrivateKey;
        this.wechatPayPublicKeyId = wechatPayPublicKeyId;
        this.wechatPayPublicKey = wechatPayPublicKey;
        this.apiV3Key = apiV3Key.clone();
        this.httpClient = httpClient;
        this.ownsHttpClient = ownsHttpClient;
        this.apiBaseUrl = apiBaseUrl;
        this.clock = clock;
        this.nonceSupplier = nonceSupplier;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 返回配置的商户号。
     *
     * @return 商户号
     */
    public String mchid() {
        return mchid;
    }

    /**
     * 从配置的 API 根地址创建固定路径端点。
     *
     * @param path API 绝对路径
     * @return 完整 URI
     */
    public URI endpoint(String path) {
        return HttpUrlBuilder.from(apiBaseUrl).path(path).build();
    }

    /**
     * 创建包含一个经过编码的动态路径段的 API 端点。
     *
     * @param prefix 动态段之前的固定路径
     * @param segment 动态路径段
     * @param suffix 动态段之后的固定路径；没有时传空字符串
     * @return 完整 URI
     */
    public URI endpoint(String prefix, String segment, String suffix) {
        HttpUrlBuilder builder = HttpUrlBuilder.from(apiBaseUrl)
                .path(prefix)
                .appendPathSegment(segment);
        if (!suffix.isEmpty()) {
            builder.appendPath(suffix);
        }
        return builder.build();
    }

    /**
     * 设置或替换 URI 的单个查询参数。
     *
     * @param uri 原 URI
     * @param name 参数名
     * @param value 参数值
     * @return 新 URI
     */
    public URI query(URI uri, String name, @Nullable String value) {
        return HttpUrlBuilder.from(uri).queryParam(name, value).build();
    }

    /**
     * 发送签名 GET 请求，验签成功后解析响应。
     *
     * @param uri 请求 URI
     * @param responseType 响应模型
     * @param <T> 响应类型
     * @return 已验签响应模型
     */
    public <T> T get(URI uri, Class<T> responseType) {
        ensureOpen();
        return send(
                HttpMethod.GET,
                uri,
                null,
                responseType
        );
    }

    /**
     * 发送签名 POST 请求，验签成功后解析响应。
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     * @param responseType 响应模型
     * @param <T> 响应类型
     * @return 已验签响应模型
     */
    public <T> T post(URI uri, Object requestBody, Class<T> responseType) {
        ensureOpen();
        return send(
                HttpMethod.POST,
                uri,
                requestBody,
                responseType
        );
    }

    /**
     * 发送期望 204 空响应的签名 POST 请求。
     *
     * @param uri 请求 URI
     * @param requestBody 请求模型
     */
    public void postNoContent(URI uri, Object requestBody) {
        ensureOpen();
        // 1. 先编码一次并复用同一正文完成签名和发送，避免二次序列化导致签名不一致。
        byte[] body = encode(requestBody);
        HttpResponse response = execute(HttpMethod.POST, uri, body);
        byte[] responseBody = response.bodyBytes();

        // 2. 失败响应按签名情况结构化；成功响应必须先验证来源，再根据 HTTP 语义处理结果。
        if (!response.isSuccessful()) {
            throw errorResponse(response.statusCode(), response.headers(), responseBody);
        }
        verify(response.headers(), responseBody);

        // 3. 关单接口的成功语义固定为 204 且无正文，拒绝异常成功响应以防协议变化被静默忽略。
        if (response.statusCode() != 204 || response.contentLength() != 0) {
            throw new WechatPayProtocolException("微信支付关单响应必须为 204 空正文");
        }
    }

    /**
     * 打开账单文件下载流。账单文件响应按官方规则不执行响应验签。
     *
     * @param uri 已由申请账单接口返回且验签通过的下载地址
     * @return 调用方负责关闭的下载流
     */
    public HttpStream openDownload(URI uri) {
        ensureOpen();
        // 1. 下载地址来自已验签的申请账单响应，仍限制来源以防被业务代码替换为任意地址。
        requireTrustedDownloadUrl(uri);
        HttpRequest request = signedRequest(HttpMethod.GET, uri, EMPTY_BODY);
        HttpStream stream;
        try {
            stream = httpClient.openStream(request);
        } catch (HttpException exception) {
            throw new WechatPayTransportException(exception);
        }
        if (stream.statusCode() == 200) {
            // 2. 成功流直接交给调用方读取和关闭，避免在传输层缓冲整个账单文件。
            return stream;
        }

        if (stream.isSuccessful()) {
            // 202 等状态不代表账单已经可用，不能当作最终文件交给调用方。
            try (stream) {
                throw new WechatPayProtocolException(
                        "微信支付账单下载响应必须为 200"
                );
            }
        }

        // 3. 错误流由本方法关闭，并限制读取上限后转换为统一的微信支付 API 异常。
        try (stream) {
            byte[] body;
            try {
                body = stream.body().readNBytes(MAX_DOWNLOAD_ERROR_BYTES + 1);
            } catch (IOException exception) {
                throw new WechatPayFileException(WechatPayFileFailure.IO, exception);
            }
            if (body.length > MAX_DOWNLOAD_ERROR_BYTES) {
                throw new WechatPayProtocolException("微信支付账单下载错误响应超过大小限制");
            }
            throw errorResponse(stream.statusCode(), stream.headers(), body);
        }
    }

    /**
     * 验证原始微信支付消息签名。
     *
     * @param headers 签名请求头
     * @param body 原始正文
     */
    public void verify(HttpHeaders headers, byte[] body) {
        ensureOpen();
        WechatPayCryptoUtils.verifyMessage(
                headers,
                body,
                wechatPayPublicKeyId,
                wechatPayPublicKey,
                clock
        );
    }

    /**
     * 解密回调资源。
     *
     * @param algorithm 加密算法
     * @param nonce 随机串
     * @param associatedData 附加数据
     * @param ciphertext 密文
     * @return 明文 JSON 字节
     */
    public byte[] decrypt(
            String algorithm,
            String nonce,
            @Nullable String associatedData,
            String ciphertext
    ) {
        ensureOpen();
        return WechatPayCryptoUtils.decrypt(
                apiV3Key,
                algorithm,
                nonce,
                associatedData,
                ciphertext
        );
    }

    /**
     * 确保根客户端仍处于可用状态。
     *
     * @throws IllegalStateException 根客户端已经关闭
     */
    public void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("WechatPayClient is closed");
        }
    }

    /**
     * 关闭传输层：清零 APIv3 密钥副本，并仅关闭本传输层拥有的 HTTP 客户端；可重复调用。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Arrays.fill(apiV3Key, (byte) 0);
            if (ownsHttpClient) {
                httpClient.close();
            }
        }
    }

    /**
     * 发送期望 200 JSON 响应的签名请求。
     *
     * @param method HTTP 请求方法
     * @param uri 已包含最终路径和查询参数的请求地址
     * @param requestBody 可选请求模型；无正文时为 {@code null}
     * @param responseType 验签通过后的 JSON 响应类型
     * @param <T> 响应模型类型
     * @return 已验签并解析的响应模型
     */
    private <T> T send(
            HttpMethod method,
            URI uri,
            @Nullable Object requestBody,
            Class<T> responseType
    ) {
        byte[] body = requestBody == null ? EMPTY_BODY : encode(requestBody);
        HttpResponse response = execute(method, uri, body);
        byte[] responseBody = response.bodyBytes();

        // 1. 失败响应按签名情况结构化；成功响应必须先验证来源，不能让未验签数据进入业务判断。
        if (!response.isSuccessful()) {
            throw errorResponse(response.statusCode(), response.headers(), responseBody);
        }
        verify(response.headers(), responseBody);
        if (response.statusCode() != 200) {
            throw new WechatPayProtocolException(
                    "微信支付 JSON API 成功响应必须为 200"
            );
        }

        // 2. 验签通过后再解析 JSON，确保模型只承载可信数据。
        if (responseBody.length == 0) {
            throw new WechatPayProtocolException("微信支付成功响应缺少正文");
        }
        try {
            return jsonCodec.read(responseBody, responseType);
        } catch (JsonException | IllegalArgumentException exception) {
            throw new WechatPayProtocolException("微信支付成功响应不是预期的 JSON 结构", exception);
        }
    }

    /**
     * 执行单次已签名 HTTP 请求，并将底层 HTTP 失败转换为脱敏领域异常。
     *
     * @param method HTTP 请求方法
     * @param uri 最终请求地址
     * @param body 与签名内容完全一致的请求正文字节
     * @return 尚未验签和解析的 HTTP 响应
     */
    private HttpResponse execute(HttpMethod method, URI uri, byte[] body) {
        // 签名在发送前即时生成，避免授权头中的时间戳和随机串被缓存或复用。
        HttpRequest request = signedRequest(method, uri, body);
        try {
            return httpClient.send(request);
        } catch (HttpException exception) {
            throw new WechatPayTransportException(exception);
        }
    }

    /**
     * 使用最终 URI 和正文创建微信支付签名请求。
     *
     * @param method HTTP 请求方法
     * @param uri 参与签名的最终请求地址
     * @param body 参与签名并原样发送的正文字节
     * @return 已携带 Authorization 和微信支付签名头的请求
     */
    private HttpRequest signedRequest(HttpMethod method, URI uri, byte[] body) {
        // 1. 每个请求使用独立时间戳和随机串，构造微信支付要求的授权签名。
        long timestamp = clock.instant().getEpochSecond();
        String nonce = nonceSupplier.get();
        String authorization = WechatPayCryptoUtils.authorization(
                mchid,
                merchantSerialNo,
                merchantPrivateKey,
                method.name(),
                uri,
                body,
                timestamp,
                nonce
        );

        // 2. 签名正文与发送正文共用同一字节数组，避免 JSON 二次序列化造成签名不一致。
        HttpRequest.Builder builder = HttpRequest.builder(uri, method)
                .header(HttpHeaderNames.ACCEPT, HttpMediaTypes.APPLICATION_JSON)
                .header(WechatPayCryptoUtils.HEADER_SERIAL, wechatPayPublicKeyId)
                .authorization(authorization)
                .userAgent("lava-pay-wechat");
        if (body.length > 0) {
            builder.body(body, HttpMediaTypes.APPLICATION_JSON);
        }
        return builder.build();
    }

    /**
     * 校验账单地址与当前环回测试源同源，或属于微信支付官方 HTTPS 域名。
     *
     * @param uri 申请账单接口返回的下载地址
     */
    private void requireTrustedDownloadUrl(URI uri) {
        // 1. 先拒绝不能唯一确定网络目标的 URI，避免用户信息或片段影响下载语义。
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawFragment() != null) {
            throw new WechatPayProtocolException("微信支付账单下载地址无效");
        }
        // 2. 测试环境允许与配置根地址同源；生产下载链接只允许微信支付官方主、备域名。
        boolean sameOrigin = sameOrigin(apiBaseUrl, uri);
        boolean officialOrigin = "https".equalsIgnoreCase(uri.getScheme())
                && WechatPayValidationUtils.OFFICIAL_API_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && (uri.getPort() == -1 || uri.getPort() == 443);
        if (!sameOrigin && !officialOrigin) {
            throw new WechatPayProtocolException("微信支付账单下载地址来源不受信任");
        }
    }

    /**
     * 比较两个 URI 的协议、主机和有效端口。
     *
     * @param left 基准地址
     * @param right 待比较地址
     * @return 两个地址的源完全一致时返回 {@code true}
     */
    private static boolean sameOrigin(URI left, URI right) {
        // 比较协议、主机和有效端口；省略端口时按协议默认端口参与比较。
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    /**
     * 返回 URI 的显式端口，未显式指定时按 HTTPS 443、其他协议 80 处理。
     *
     * @param uri 待解析端口的地址
     * @return 用于同源比较的有效端口
     */
    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * 将请求对象编码为唯一参与签名和发送的 JSON 字节。
     *
     * @param value 待编码的请求模型
     * @return UTF-8 JSON 正文字节
     */
    private byte[] encode(Object value) {
        try {
            return jsonCodec.writeBytes(value);
        } catch (JsonException exception) {
            throw new WechatPayProtocolException("无法编码微信支付请求 JSON", exception);
        }
    }

    /**
     * 将失败响应转换为结构化 API 异常。
     *
     * <p>微信支付网关层错误（如请求签名错误返回的 401 {@code SIGN_ERROR}）不带签名头，
     * 按未验签错误返回，供调用方诊断；带签名头的错误必须验签通过。</p>
     *
     * @param statusCode HTTP 响应状态码
     * @param headers 响应头
     * @param body 原始错误正文
     * @return 结构化微信支付 API 异常
     */
    private WechatPayApiException errorResponse(int statusCode, HttpHeaders headers, byte[] body) {
        boolean signed = headers.get(WechatPayCryptoUtils.HEADER_SIGNATURE) != null;
        if (signed) {
            verify(headers, body);
        }
        return apiException(statusCode, signed, headers, body);
    }

    /**
     * 将错误正文转换为结构化 API 异常。
     *
     * @param statusCode HTTP 响应状态码
     * @param verified 错误正文是否已通过签名验证
     * @param headers 用于提取 Request-ID 的响应头
     * @param body 原始错误正文
     * @return 结构化微信支付 API 异常
     */
    private WechatPayApiException apiException(
            int statusCode,
            boolean verified,
            HttpHeaders headers,
            byte[] body
    ) {
        // 1. 错误响应也必须符合微信支付约定的 JSON 结构，不能将任意正文包装成业务异常。
        ApiErrorPayload error;
        try {
            error = jsonCodec.read(body, ApiErrorPayload.class);
        } catch (JsonException exception) {
            throw new WechatPayProtocolException("微信支付错误响应不是预期的 JSON 结构", exception);
        }
        if (error.code() == null || error.code().isBlank() || error.message() == null) {
            throw new WechatPayProtocolException("微信支付错误响应缺少 code 或 message");
        }

        // 2. 将可选明细和请求 ID 一并保留，便于调用方定位具体字段及向微信支付排障
        return new WechatPayApiException(
                statusCode,
                verified,
                error.code(),
                error.message(),
                error.detail(),
                headers.get(WechatPayCryptoUtils.HEADER_REQUEST_ID)
        );
    }

    /**
     * 微信支付错误响应正文。
     *
     * @param code    错误码；缺失时视为协议失败
     * @param message 错误描述，可能含业务上下文，不自动写入异常消息
     * @param detail  可选的字段级错误明细
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorPayload(
            @Nullable String code,
            @Nullable String message,
            @Nullable WechatPayApiErrorDetail detail) {
    }
}
