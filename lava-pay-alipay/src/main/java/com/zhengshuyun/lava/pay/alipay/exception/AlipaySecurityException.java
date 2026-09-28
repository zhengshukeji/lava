/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.exception;

/**
 * 支付宝消息来源或业务一致性校验失败。
 */
public final class AlipaySecurityException extends AlipayException {
    /** 稳定的安全校验失败分类。 */
    private final AlipaySecurityFailure failure;

    /**
     * 创建安全异常。
     *
     * @param failure 稳定失败分类
     */
    public AlipaySecurityException(AlipaySecurityFailure failure) {
        super("支付宝安全校验失败：" + failure);
        this.failure = failure;
    }

    /**
     * 创建保留底层异常的安全校验异常。
     *
     * @param failure 精确失败类别
     * @param cause   底层异常
     */
    public AlipaySecurityException(AlipaySecurityFailure failure, Throwable cause) {
        super("支付宝安全校验失败：" + failure, cause);
        this.failure = failure;
    }

    /**
     * 获取稳定的安全校验失败分类。
     *
     * @return 稳定失败分类
     */
    public AlipaySecurityFailure failure() {
        return failure;
    }
}
