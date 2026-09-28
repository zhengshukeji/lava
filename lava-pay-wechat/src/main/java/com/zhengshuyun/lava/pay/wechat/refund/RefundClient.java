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

package com.zhengshuyun.lava.pay.wechat.refund;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityException;
import com.zhengshuyun.lava.pay.wechat.exception.WechatPaySecurityFailure;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayTransport;
import com.zhengshuyun.lava.pay.wechat.internal.WechatPayValidationUtils;
import org.jspecify.annotations.Nullable;

/**
 * 普通支付退款申请与查询客户端。
 */
public final class RefundClient {
    /** 普通支付退款申请及按商户退款单号查询的 APIv3 固定路径。 */
    private static final String REFUND_PATH = "/v3/refund/domestic/refunds";

    /** 根客户端共享的签名传输层与关闭状态。 */
    private final WechatPayTransport transport;

    /**
     * 由根客户端创建退款入口。
     *
     * @param transport 共享协议传输层
     */
    public RefundClient(WechatPayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 申请全部或部分退款。成功仅表示微信支付已受理，最终结果应结合通知或查询确认。
     *
     * @param request 退款申请
     * @return 已验签退款单
     */
    public Refund apply(RefundRequest request) {
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        Refund refund = transport.post(transport.endpoint(REFUND_PATH), request,
                Refund.class);
        requireApplyResponse(request, refund);
        return refund;
    }

    /**
     * 使用商户退款单号查询单笔退款。
     *
     * @param outRefundNo 商户退款单号
     * @return 已验签退款单
     */
    public Refund queryByOutRefundNo(String outRefundNo) {
        transport.ensureOpen();
        outRefundNo = WechatPayValidationUtils.requireOutRefundNo(outRefundNo);
        Refund refund = transport.get(transport.endpoint(REFUND_PATH, outRefundNo, ""),
                Refund.class);
        requireSame(outRefundNo, refund.outRefundNo());
        return refund;
    }

    /**
     * 将退款申请响应的业务标识和金额绑定到原请求，防止已验签的其他退款被误返回。
     *
     * @param request 本次退款申请
     * @param refund 微信支付返回的已验签退款单
     * @throws WechatPaySecurityException 退款单号、原订单标识或金额与请求不一致时抛出
     */
    private static void requireApplyResponse(RefundRequest request, Refund refund) {
        // 1. 先核对幂等退款单号和原支付订单，避免把其他业务单的已验签响应交给调用方。
        requireSame(request.outRefundNo(), refund.outRefundNo());
        if (request.transactionId() != null) {
            requireSame(request.transactionId(), refund.transactionId());
        } else {
            requireSame(request.outTradeNo(), refund.outTradeNo());
        }

        // 2. 同一退款单号重试时微信可能返回已受理结果，金额必须仍与本次请求完全一致。
        if (refund.amount() == null
                || request.amount().refund() != refund.amount().refund()
                || request.amount().total() != refund.amount().total()) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.RESPONSE_MISMATCH);
        }
    }

    /**
     * 校验请求与响应中的业务标识严格一致。
     *
     * @param expected 请求携带的期望标识；不应为 {@code null}
     * @param actual 已验签响应中的实际标识
     * @throws WechatPaySecurityException 期望标识缺失或两者不一致时抛出
     */
    private static void requireSame(@Nullable String expected, String actual) {
        if (expected == null || !expected.equals(actual)) {
            throw new WechatPaySecurityException(WechatPaySecurityFailure.RESPONSE_MISMATCH);
        }
    }
}
