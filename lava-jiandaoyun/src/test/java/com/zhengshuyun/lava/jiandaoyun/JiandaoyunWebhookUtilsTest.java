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

import com.zhengshuyun.lava.jiandaoyun.data.DataRecord;
import com.zhengshuyun.lava.jiandaoyun.exception.JiandaoyunProtocolException;
import com.zhengshuyun.lava.jiandaoyun.webhook.JiandaoyunWebhookUtils;
import com.zhengshuyun.lava.jiandaoyun.webhook.WebhookEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简道云推送验签与解析测试。
 */
class JiandaoyunWebhookUtilsTest {
    /** 推送原始正文。 */
    private static final String PAYLOAD =
            "{\"op\":\"data_create\",\"opTime\":1700000000000,\"data\":{\"_id\":\"d1\",\"_widget_1\":\"张三\"}}";
    /**
     * 期望签名，由 {@code printf '%s' 'nonce123:<PAYLOAD>:my-secret:1700000000' | shasum -a 1}
     * 独立计算，不依赖被测实现。
     */
    private static final String SIGNATURE = "d9e89f247e503dac85a04eade70771cc33d0e4d9";

    /**
     * 验证签名算法与外部独立计算结果一致。
     */
    @Test
    void signMatchesIndependentSha1() {
        assertEquals(SIGNATURE, JiandaoyunWebhookUtils.sign("my-secret", "nonce123", "1700000000", PAYLOAD));
    }

    /**
     * 验证正确签名通过（大小写不敏感），篡改正文、错误密钥或缺失签名均不通过。
     */
    @Test
    void verifyAcceptsOnlyMatchingSignature() {
        assertTrue(JiandaoyunWebhookUtils.verify("my-secret", "nonce123", "1700000000", PAYLOAD, SIGNATURE));
        assertTrue(JiandaoyunWebhookUtils.verify("my-secret", "nonce123", "1700000000", PAYLOAD,
                SIGNATURE.toUpperCase()));
        assertFalse(JiandaoyunWebhookUtils.verify("my-secret", "nonce123", "1700000000", PAYLOAD + " ", SIGNATURE));
        assertFalse(JiandaoyunWebhookUtils.verify("other", "nonce123", "1700000000", PAYLOAD, SIGNATURE));
        assertFalse(JiandaoyunWebhookUtils.verify("my-secret", "nonce123", "1700000000", PAYLOAD, null));
    }

    /**
     * 验证数据推送解析为事件与数据记录。
     */
    @Test
    void parseDataEvent() {
        WebhookEvent event = JiandaoyunWebhookUtils.parse(PAYLOAD);

        assertEquals(WebhookEvent.DATA_CREATE, event.op());
        assertEquals(Instant.ofEpochMilli(1700000000000L), event.opInstant());
        assertTrue(event.isDataEvent());
        DataRecord record = event.asDataRecord();
        assertEquals("d1", record.id());
        assertEquals("张三", record.value("_widget_1"));
    }

    /**
     * 验证非 JSON 或缺少 op 的正文按协议异常处理。
     */
    @Test
    void parseRejectsMalformedPayload() {
        assertThrows(JiandaoyunProtocolException.class, () -> JiandaoyunWebhookUtils.parse("not-json"));
        assertThrows(JiandaoyunProtocolException.class, () -> JiandaoyunWebhookUtils.parse("{\"data\":{}}"));
    }
}
