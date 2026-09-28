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

package com.zhengshuyun.lava.pay.wechat.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityFailure;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * 普通支付交易查单和关单客户端。
 */
public final class TransactionClient {
    /** 按商户订单号查单及关单的 APIv3 路径前缀。 */
    private static final String OUT_TRADE_NO_PREFIX = "/v3/pay/transactions/out-trade-no";

    /** 按微信支付订单号查单的 APIv3 路径前缀。 */
    private static final String TRANSACTION_ID_PREFIX = "/v3/pay/transactions/id";

    /** 根客户端共享的签名传输层与关闭状态。 */
    private final WechatPayTransport transport;

    /**
     * 由根客户端创建交易入口。
     *
     * @param transport 共享协议传输层
     */
    public TransactionClient(WechatPayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 使用商户订单号查询订单。未支付订单只能使用此方式查询。
     *
     * @param outTradeNo 商户订单号
     * @return 已验签交易状态
     */
    public Transaction queryByOutTradeNo(String outTradeNo) {
        transport.ensureOpen();
        outTradeNo = WechatPayValidationUtils.requireOutTradeNo(outTradeNo);
        URI uri = transport.endpoint(OUT_TRADE_NO_PREFIX, outTradeNo, "");
        Transaction transaction = transport.get(
                transport.query(uri, "mchid", transport.mchid()), Transaction.class);
        requireMchid(transport, transaction);
        requireIdentifier(outTradeNo, transaction.outTradeNo());
        return transaction;
    }

    /**
     * 使用微信支付订单号查询已支付订单。
     *
     * @param transactionId 微信支付订单号
     * @return 已验签交易状态
     */
    public Transaction queryByTransactionId(String transactionId) {
        transport.ensureOpen();
        transactionId = ValidationUtils.requireNotBlank(transactionId, "transactionId must not be blank");
        URI uri = transport.endpoint(TRANSACTION_ID_PREFIX, transactionId, "");
        Transaction transaction = transport.get(
                transport.query(uri, "mchid", transport.mchid()), Transaction.class);
        requireMchid(transport, transaction);
        requireIdentifier(transactionId, transaction.transactionId());
        return transaction;
    }

    /**
     * 关闭仍处于未支付状态的订单。
     *
     * @param outTradeNo 商户订单号
     */
    public void close(String outTradeNo) {
        transport.ensureOpen();
        outTradeNo = WechatPayValidationUtils.requireOutTradeNo(outTradeNo);
        URI uri = transport.endpoint(OUT_TRADE_NO_PREFIX, outTradeNo, "close");
        transport.postNoContent(uri, new ClosePayload(transport.mchid()));
    }

    /**
     * 将查单响应商户号绑定到当前根客户端，防止其他商户的已验签交易被误返回。
     *
     * @param transport 持有期望商户号的传输层
     * @param transaction 已验签的查单响应
     */
    private static void requireMchid(WechatPayTransport transport,
                                     Transaction transaction) {
        if (!transport.mchid().equals(transaction.mchid())) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.MERCHANT_MISMATCH);
        }
    }

    /**
     * 校验查单响应标识与请求目标一致。
     *
     * @param expected 请求路径中的商户订单号或微信支付订单号
     * @param actual 已验签响应中的实际订单标识；协议缺失时为 {@code null}
     */
    private static void requireIdentifier(String expected, @Nullable String actual) {
        if (!expected.equals(actual)) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.RESPONSE_MISMATCH);
        }
    }

    /**
     * 关单接口的最小请求载荷，用于将目标订单绑定到当前普通商户。
     *
     * @param mchid 当前根客户端配置的商户号
     */
    private record ClosePayload(@JsonProperty("mchid") String mchid) {
    }
}
