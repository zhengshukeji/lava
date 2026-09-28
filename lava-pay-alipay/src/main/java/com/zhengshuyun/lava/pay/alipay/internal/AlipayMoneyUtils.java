/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import org.jspecify.annotations.Nullable;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 支付宝元字符串与公开分金额之间的无损转换工具。
 */
public final class AlipayMoneyUtils {
    /** 支付宝人民币金额格式，固定保留两位小数且禁止科学计数法。 */
    private static final Pattern MONEY = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");

    /** 禁止实例化支付宝金额工具。 */
    private AlipayMoneyUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将正数分金额转换为固定两位小数的元字符串。
     *
     * @param cents 金额，单位为分
     * @return 固定两位小数的元字符串
     */
    public static String formatPositive(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return formatNonNegative(cents);
    }

    /**
     * 将非负分金额转换为固定两位小数的元字符串。
     *
     * @param cents 金额，单位为分
     * @return 固定两位小数的元字符串
     */
    public static String formatNonNegative(long cents) {
        if (cents < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        return cents / 100 + "." + (cents % 100 < 10 ? "0" : "") + cents % 100;
    }

    /**
     * 将支付宝金额文本精确解析为分，拒绝多于两位小数或数值溢出。
     *
     * @param value 协议金额文本
     * @param name  字段名
     * @return 金额，单位为分
     */
    public static long parse(String value, String name) {
        if (value == null || !MONEY.matcher(value).matches()) {
            throw new AlipayProtocolException("支付宝响应字段 " + name + " 不是有效金额");
        }
        try {
            return new BigDecimal(value).movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new AlipayProtocolException("支付宝响应字段 " + name + " 超出金额范围", exception);
        }
    }

    /**
     * 解析可选金额字段；字段缺失时返回 null。
     *
     * @param value 协议金额文本，可以为 null
     * @param name  字段名
     * @return 金额，单位为分；缺失时为 null
     */
    public static @Nullable Long parseOptional(@Nullable String value, String name) {
        return value == null ? null : parse(value, name);
    }
}
