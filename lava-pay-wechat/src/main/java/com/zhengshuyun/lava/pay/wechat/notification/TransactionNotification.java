/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.wechat.notification;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.pay.wechat.transaction.Transaction;

import java.time.OffsetDateTime;

/**
 * 已验签并解密的支付成功通知。
 *
 * @param id 通知唯一编号，业务侧可用于辅助幂等
 * @param createTime 通知创建时间
 * @param eventType 固定为 TRANSACTION.SUCCESS
 * @param summary 通知摘要
 * @param transaction 支付成功交易
 */
public record TransactionNotification(
        String id,
        OffsetDateTime createTime,
        String eventType,
        String summary,
        Transaction transaction
) {

    /**
     * 使用后端可信订单记录核对通知中的应用、订单号和金额。
     *
     * <p>通知验签只能证明消息来自微信支付，业务入账前仍必须完成此项核对。</p>
     *
     * @param expectedAppid      可信应用 ID
     * @param expectedOutTradeNo 可信商户订单号
     * @param expectedTotal      可信订单总金额，单位为分
     * @return 当前通知
     */
    public TransactionNotification requireOrder(
            String expectedAppid,
            String expectedOutTradeNo,
            long expectedTotal
    ) {
        transaction.requireOrder(
                expectedAppid,
                expectedOutTradeNo,
                expectedTotal
        );
        return this;
    }

    /**
     * 使用后端已经保存的微信侧标识完整核对支付成功通知。
     *
     * @param expectedAppid         可信应用 ID
     * @param expectedOutTradeNo    可信商户订单号
     * @param expectedTransactionId 可信微信支付订单号
     * @param expectedOpenid        可信付款人 OpenID
     * @param expectedTotal         可信订单总金额，单位为分
     * @return 当前通知
     */
    public TransactionNotification requirePaidOrder(
            String expectedAppid,
            String expectedOutTradeNo,
            String expectedTransactionId,
            String expectedOpenid,
            long expectedTotal
    ) {
        transaction.requirePaidOrder(
                expectedAppid,
                expectedOutTradeNo,
                expectedTransactionId,
                expectedOpenid,
                expectedTotal
        );
        return this;
    }
}
