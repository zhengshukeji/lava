/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.order;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

/**
 * 支付宝电脑网站与手机网站支付共用的自动提交 HTML 表单。
 *
 * <p>调用方应将 {@link #html()} 原样作为 {@value #CONTENT_TYPE} 响应正文输出。
 * 该值不是 URL，不能赋给浏览器的 {@code location}。</p>
 *
 * @param html 完整自动提交表单
 */
public record PayForm(String html) {
    /** 推荐的 HTTP Content-Type。 */
    public static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    /**
     * 创建支付表单。
     *
     * @param html 完整 HTML
     */
    public PayForm {
        ValidationUtils.requireNotBlank(html, "html must not be blank");
    }
}
