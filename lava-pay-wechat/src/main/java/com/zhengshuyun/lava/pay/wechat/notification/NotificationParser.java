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

package com.zhengshuyun.lava.pay.wechat.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpHeaders;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPayProtocolException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityFailure;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.transaction.TradeState;
import com.zhengshuyun.lava.pay.wechat.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 不依赖 Servlet、Spring 或其他 Web 框架的微信支付通知解析器。
 *
 * <p>调用方必须传入框架接收到的原始请求正文。解析成功后应尽快向微信支付返回 200 或 204，
 * 业务幂等、持久化和异步处理不由本工具包负责。</p>
 */
public final class NotificationParser {

    /** 本解析器支持的通知资源类型，表示业务资源需要 AES-GCM 解密。 */
    private static final String RESOURCE_TYPE = "encrypt-resource";

    /** 支付成功通知的唯一支持事件类型。 */
    private static final String TRANSACTION_EVENT = "TRANSACTION.SUCCESS";

    /** 退款通知允许的成功、异常和关闭事件类型。 */
    private static final Set<String> REFUND_EVENTS = Set.of(
            "REFUND.SUCCESS", "REFUND.ABNORMAL", "REFUND.CLOSED");

    /** 根客户端共享的验签、解密能力与关闭状态。 */
    private final WechatPayTransport transport;

    /** 验签后的通知信封与解密业务资源使用的 JSON 编解码器。 */
    private final JsonCodec jsonCodec = JsonCodec.defaultCodec();

    /**
     * 由根客户端创建通知解析器。
     *
     * @param transport 共享协议传输层
     */
    public NotificationParser(WechatPayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 验签、解密并解析支付成功通知。
     *
     * @param headers 原始 HTTP 请求头
     * @param body 未修改的原始 UTF-8 请求正文
     * @return 支付成功通知
     */
    public TransactionNotification parseTransaction(HttpHeaders headers, String body) {
        ValidationUtils.requireNonNull(body, "body must not be null");
        return parseTransaction(headers, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验签、解密并解析支付成功通知。
     *
     * @param headers 原始 HTTP 请求头
     * @param body 未修改的原始请求正文字节
     * @return 支付成功通知
     */
    public TransactionNotification parseTransaction(HttpHeaders headers, byte[] body) {
        // 1. 使用原始请求头和正文验签，再解析并校验支付通知信封类型。
        transport.ensureOpen();
        NotificationEnvelope envelope = verifiedEnvelope(transport, headers, body);
        requireEnvelope(envelope, TRANSACTION_EVENT, "transaction");

        // 2. 仅对已经验签且类型匹配的资源执行 AES-GCM 解密和业务 JSON 解析。
        byte[] plaintext = decrypt(transport, envelope.resource());
        Transaction transaction = read(
                plaintext,
                Transaction.class,
                "支付通知资源不是预期的 JSON 结构"
        );

        // 3. 将商户号绑定到当前客户端，并确认资源确实是成功交易，再返回可信通知模型。
        //    TRANSACTION.SUCCESS 事件承载非 SUCCESS 交易属于协议矛盾，requireOrder 对未付款交易
        //    容忍缺失金额，若不在此拦截，只做订单核对的调用方可能把未付款订单当作已支付处理
        requireMchid(transport, transaction.mchid());
        if (!TradeState.SUCCESS.equals(transaction.tradeState())) {
            throw new WechatPayProtocolException("支付成功通知的 tradeState 必须为 SUCCESS");
        }
        return new TransactionNotification(
                envelope.id(),
                envelope.createTime(),
                envelope.eventType(),
                envelope.summary(),
                transaction
        );
    }

    /**
     * 验签、解密并解析退款状态变更通知。
     *
     * @param headers 原始 HTTP 请求头
     * @param body 未修改的原始 UTF-8 请求正文
     * @return 退款通知
     */
    public RefundNotification parseRefund(HttpHeaders headers, String body) {
        ValidationUtils.requireNonNull(body, "body must not be null");
        return parseRefund(headers, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验签、解密并解析退款状态变更通知。
     *
     * @param headers 原始 HTTP 请求头
     * @param body 未修改的原始请求正文字节
     * @return 退款通知
     */
    public RefundNotification parseRefund(HttpHeaders headers, byte[] body) {
        // 1. 使用原始请求头和正文验签，再校验退款通知事件与资源类型。
        transport.ensureOpen();
        NotificationEnvelope envelope = verifiedEnvelope(transport, headers, body);
        if (!REFUND_EVENTS.contains(envelope.eventType())) {
            throw new WechatPayProtocolException("退款通知 eventType 不受支持");
        }
        requireEnvelope(envelope, envelope.eventType(), "refund");

        // 2. 仅对已经验签且类型匹配的资源执行 AES-GCM 解密和业务 JSON 解析。
        byte[] plaintext = decrypt(transport, envelope.resource());
        RefundNotification.Resource refund = read(plaintext,
                RefundNotification.Resource.class,
                "退款通知资源不是预期的 JSON 结构");
        // 3. 将商户号绑定到当前客户端，并要求退款状态与事件类型一致，再返回可信通知模型
        requireMchid(transport, refund.mchid());
        requireRefundStatus(envelope.eventType(), refund);
        return new RefundNotification(
                envelope.id(),
                envelope.createTime(),
                envelope.eventType(),
                envelope.summary(),
                refund
        );
    }

    /**
     * 验证原始消息并解析出字段完整的通知信封。
     *
     * @param transport 当前根客户端的验签传输层
     * @param headers 未修改的通知请求头
     * @param body 未修改的通知正文字节
     * @return 已验签且必填字段完整的通知信封
     */
    private NotificationEnvelope verifiedEnvelope(WechatPayTransport transport,
                                                  HttpHeaders headers,
                                                  byte[] body) {
        ValidationUtils.requireNonNull(headers, "headers must not be null");
        ValidationUtils.requireNonNull(body, "body must not be null");
        // 1. 必须使用原始正文完成验签，未经验证的 JSON 不进入后续分支判断
        transport.verify(headers, body);
        // 2. 验签通过后再解释通知信封，防止攻击者驱动解密或类型路由
        NotificationEnvelope envelope = read(body, NotificationEnvelope.class,
                "微信支付通知信封不是预期的 JSON 结构");
        // 3. 信封字段是通知模型的非空契约：id 是调用方的幂等键，event_type 决定解析路由，
        //    缺失时统一报协议异常，而不是让 null 流入业务或在集合判断处抛 NPE
        requireText(envelope.id(), "id");
        requireField(envelope.createTime(), "create_time");
        requireText(envelope.eventType(), "event_type");
        requireText(envelope.summary(), "summary");
        requireField(envelope.resource(), "resource");
        return envelope;
    }

    /**
     * 校验通知事件、资源类型和业务资源原始类型。
     *
     * @param envelope 已验签的通知信封
     * @param eventType 当前解析入口期望的事件类型
     * @param originalType 当前解析入口期望的解密资源类型
     */
    private static void requireEnvelope(NotificationEnvelope envelope, String eventType,
                                        String originalType) {
        if (!eventType.equals(envelope.eventType())) {
            throw new WechatPayProtocolException("微信支付通知 eventType 不受支持");
        }
        if (!RESOURCE_TYPE.equals(envelope.resourceType())) {
            throw new WechatPayProtocolException("微信支付通知 resourceType 不受支持");
        }
        if (!originalType.equals(Objects.requireNonNull(envelope.resource()).originalType())) {
            throw new WechatPayProtocolException("微信支付通知 originalType 不匹配");
        }
    }

    /**
     * 解密已经完成类型校验的通知资源。
     *
     * @param transport 提供 APIv3 密钥和 AES-GCM 解密能力的传输层
     * @param resource 已校验必填字段的加密资源
     * @return 待解析的 UTF-8 JSON 明文字节
     */
    private static byte[] decrypt(WechatPayTransport transport,
                                  @Nullable EncryptedResource resource) {
        Objects.requireNonNull(resource, "resource");
        // 3. 仅在信封类型校验通过后解密，并由 AES-GCM 认证标签验证密文完整性。
        return transport.decrypt(
                resource.algorithm(),
                resource.nonce(),
                resource.associatedData(),
                resource.ciphertext()
        );
    }

    /**
     * 将解密资源中的商户号绑定到当前根客户端。
     *
     * @param transport 持有期望商户号的传输层
     * @param actualMchid 解密业务资源中的实际商户号
     */
    private static void requireMchid(WechatPayTransport transport,
                                     String actualMchid) {
        if (!transport.mchid().equals(actualMchid)) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.MERCHANT_MISMATCH);
        }
    }

    /**
     * 校验退款事件类型与解密资源状态一致，并要求成功事件携带成功时间。
     *
     * @param eventType 已验签通知信封中的退款事件类型
     * @param refund 解密并解析得到的退款资源
     */
    private static void requireRefundStatus(String eventType, RefundNotification.Resource refund) {
        String expectedStatus = eventType.substring("REFUND.".length());
        if (!expectedStatus.equals(refund.refundStatus())) {
            throw new WechatPayProtocolException("退款通知 eventType 与 refundStatus 不匹配");
        }
        if ("SUCCESS".equals(expectedStatus) && refund.successTime() == null) {
            throw new WechatPayProtocolException("退款成功通知缺少 successTime");
        }
    }

    private static void requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WechatPayProtocolException("微信支付通知缺少必填字段 " + field);
        }
    }

    private static void requireField(@Nullable Object value, String field) {
        if (value == null) {
            throw new WechatPayProtocolException("微信支付通知缺少必填字段 " + field);
        }
    }

    /**
     * 将已验证字节严格解码为指定通知模型，统一隐藏 JSON 底层解析细节。
     *
     * @param body 已验签的信封字节或已认证的解密明文
     * @param type 目标通知模型类型
     * @param failureMessage JSON 格式不符合预期时的安全错误文本
     * @param <T> 目标模型类型
     * @return 非空的通知模型
     */
    private <T> T read(byte[] body, Class<T> type, String failureMessage) {
        try {
            T result = jsonCodec.read(body, type);
            if (result == null) {
                throw new WechatPayProtocolException(failureMessage);
            }
            return result;
        } catch (JsonException | IllegalArgumentException exception) {
            throw new WechatPayProtocolException(failureMessage, exception);
        }
    }

    /**
     * 通知信封。验签通过后才解析，必填字段由 {@link #verifiedEnvelope} 统一检查。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotificationEnvelope(
            @JsonProperty("id") String id,
            @JsonProperty("create_time") OffsetDateTime createTime,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("resource_type") @Nullable String resourceType,
            @JsonProperty("summary") String summary,
            @JsonProperty("resource") @Nullable EncryptedResource resource) {
    }

    /**
     * 通知中的加密资源。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EncryptedResource(
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("ciphertext") String ciphertext,
            @JsonProperty("associated_data") @Nullable String associatedData,
            @JsonProperty("original_type") @Nullable String originalType,
            @JsonProperty("nonce") String nonce) {
    }
}
