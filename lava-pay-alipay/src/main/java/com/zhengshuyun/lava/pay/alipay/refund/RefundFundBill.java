/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.refund;

import org.jspecify.annotations.Nullable;

/**
 * 退款使用的单个资金渠道。
 *
 * @param fundChannel 资金渠道原始标识
 * @param amount      使用金额，单位为分
 * @param realAmount  渠道实际退款金额，单位为分；没有时为 {@code null}
 * @param fundType    银行卡资金类型；没有时为 {@code null}
 */
public record RefundFundBill(
        @Nullable String fundChannel,
        long amount,
        @Nullable Long realAmount,
        @Nullable String fundType
) {
}
