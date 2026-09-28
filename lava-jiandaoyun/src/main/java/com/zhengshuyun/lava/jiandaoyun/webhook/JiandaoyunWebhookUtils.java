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

package com.zhengshuyun.lava.jiandaoyun.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import com.zhengshuyun.lava.jiandaoyun.internal.JiandaoyunJsonUtils;
import com.zhengshuyun.lava.json.JsonException;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 简道云推送（Webhook）的验签与解析工具。
 *
 * <p>简道云推送请求的 URL 查询参数携带 {@code nonce} 与 {@code timestamp}，请求头
 * {@code X-JDY-Signature} 携带签名。接收方应先用原始请求正文验签，再解析：</p>
 * <pre>{@code
 * if (!JiandaoyunWebhookUtils.verify(secret, nonce, timestamp, rawBody, signature)) {
 *     return 401;
 * }
 * WebhookEvent event = JiandaoyunWebhookUtils.parse(rawBody);
 * if (event.isDataEvent()) {
 *     DataRecord record = event.asDataRecord();
 * }
 * }</pre>
 */
public final class JiandaoyunWebhookUtils {
    /** 签名请求头名称。 */
    public static final String SIGNATURE_HEADER = "X-JDY-Signature";

    /** 禁止实例化推送工具。 */
    private JiandaoyunWebhookUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 计算推送签名：{@code sha1_hex(utf8("<nonce>:<payload>:<secret>:<timestamp>"))}。
     *
     * @param secret 推送设置中的签名密钥
     * @param nonce URL 查询参数 nonce
     * @param timestamp URL 查询参数 timestamp
     * @param payload 原始请求正文，不能是反序列化后再序列化的文本
     * @return 小写十六进制签名
     */
    public static String sign(String secret, String nonce, String timestamp, String payload) {
        ValidationUtils.requireNotBlank(secret, "secret must not be blank");
        ValidationUtils.requireNonNull(nonce, "nonce must not be null");
        ValidationUtils.requireNonNull(timestamp, "timestamp must not be null");
        ValidationUtils.requireNonNull(payload, "payload must not be null");
        String content = nonce + ':' + payload + ':' + secret + ':' + timestamp;
        return HexFormat.of().formatHex(sha1().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 校验推送签名，使用常量时间比较防止计时攻击。
     *
     * @param secret 推送设置中的签名密钥
     * @param nonce URL 查询参数 nonce
     * @param timestamp URL 查询参数 timestamp
     * @param payload 原始请求正文
     * @param signature 请求头 {@value #SIGNATURE_HEADER} 的值；缺失时传 {@code null}
     * @return 签名有效时为 {@code true}
     */
    public static boolean verify(String secret, String nonce, String timestamp, String payload,
                                 @Nullable String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(secret, nonce, timestamp, payload).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 解析推送正文。
     *
     * @param payload 原始请求正文
     * @return 推送事件
     * @throws JiandaoyunProtocolException 正文不是 {@code {"op", "opTime", "data"}} 结构时抛出
     */
    public static WebhookEvent parse(String payload) {
        ValidationUtils.requireNonNull(payload, "payload must not be null");
        EventPayload event;
        try {
            event = JiandaoyunJsonUtils.codec().read(payload, EventPayload.class);
        } catch (JsonException exception) {
            throw new JiandaoyunProtocolException("简道云推送正文不是合法 JSON", exception);
        }
        if (event.op() == null || event.op().isBlank()) {
            throw new JiandaoyunProtocolException("简道云推送正文缺少 op");
        }
        return new WebhookEvent(event.op(), event.opTime(), event.data() == null ? Map.of() : event.data());
    }

    /**
     * 创建 SHA-1 摘要器；该算法是 JDK 必备算法。
     *
     * @return 摘要器
     */
    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-1 算法", exception);
        }
    }

    /**
     * 推送正文结构。
     *
     * @param op 事件类型
     * @param opTime 事件触发时间（毫秒）
     * @param data 推送内容
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EventPayload(@Nullable String op, @Nullable Long opTime, @Nullable Map<String, Object> data) {
    }
}
