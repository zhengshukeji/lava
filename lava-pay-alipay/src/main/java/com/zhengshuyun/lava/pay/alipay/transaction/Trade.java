/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.transaction;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipaySecurityFailure;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 已验签的支付宝交易状态。
 *
 * @param tradeNo         支付宝交易号；尚未生成时为 {@code null}
 * @param outTradeNo      商户订单号
 * @param tradeState      原始交易状态
 * @param totalAmount     订单金额，单位为分
 * @param buyerOpenId     买家 OpenID；没有时为 {@code null}
 * @param buyerUserId     兼容存量商户的买家用户 ID；没有时为 {@code null}
 * @param buyerLogonId    脱敏买家登录账号；没有时为 {@code null}
 * @param sendPayDate     打款时间；没有时为 {@code null}
 * @param buyerPayAmount  买家实付金额，单位为分；没有时为 {@code null}
 * @param receiptAmount   商家实收金额，单位为分；没有时为 {@code null}
 * @param invoiceAmount   可开票金额，单位为分；没有时为 {@code null}
 * @param pointAmount     积分支付金额，单位为分；没有时为 {@code null}
 * @param storeId         商户门店号；没有时为 {@code null}
 * @param fundBills       支付资金渠道列表
 */
public record Trade(
        @Nullable String tradeNo,
        String outTradeNo,
        String tradeState,
        long totalAmount,
        @Nullable String buyerOpenId,
        @Nullable String buyerUserId,
        @Nullable String buyerLogonId,
        @Nullable LocalDateTime sendPayDate,
        @Nullable Long buyerPayAmount,
        @Nullable Long receiptAmount,
        @Nullable Long invoiceAmount,
        @Nullable Long pointAmount,
        @Nullable String storeId,
        List<FundBill> fundBills
) {

    /**
     * 使用后端可信订单记录核对商户订单号和订单金额。
     *
     * @param expectedOutTradeNo 可信商户订单号
     * @param expectedAmount     可信订单金额，单位为分
     * @return 当前交易
     * @throws AlipaySecurityException 任一关键字段不匹配
     */
    public Trade requireOrder(String expectedOutTradeNo, long expectedAmount) {
        expectedOutTradeNo = AlipayValidationUtils.requireOutTradeNo(
                expectedOutTradeNo
        );
        ValidationUtils.requireTrue(
                expectedAmount > 0,
                "expectedAmount must be positive"
        );
        if (!outTradeNo.equals(expectedOutTradeNo) || totalAmount != expectedAmount) {
            throw new AlipaySecurityException(
                    AlipaySecurityFailure.RESPONSE_MISMATCH
            );
        }
        return this;
    }

    /**
     * 判断交易是否已完成付款。
     *
     * @return 仅在交易状态为支付成功或交易完成时返回 {@code true}
     */
    public boolean paid() {
        return TradeState.TRADE_SUCCESS.equals(tradeState)
                || TradeState.TRADE_FINISHED.equals(tradeState);
    }

    /**
     * 单个支付资金渠道。
     *
     * @param fundChannel 资金渠道原始标识
     * @param amount      使用金额，单位为分
     * @param realAmount  渠道实际付款金额，单位为分；没有时为 {@code null}
     */
    public record FundBill(@Nullable String fundChannel, long amount, @Nullable Long realAmount) {
    }
}
