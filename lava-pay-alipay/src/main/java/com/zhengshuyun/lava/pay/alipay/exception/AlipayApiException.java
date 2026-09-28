/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 支付宝 OpenAPI V3 返回的结构化 API 失败。
 *
 * <p>{@link #verified()} 表示错误正文是否通过支付宝响应签名验证。支付宝官方 V3 SDK 允许错误响应
 * 不携带签名，因此调用方只能将未验签错误用于诊断和重试分类，不能据此更新支付业务状态。</p>
 */
public final class AlipayApiException extends AlipayException {
    /** 支付宝返回的 HTTP 状态码。 */
    private final int statusCode;
    /** 错误正文是否通过支付宝响应签名验证。 */
    private final boolean verified;
    /** 支付宝 V3 错误码。 */
    private final String code;
    /** 支付宝 V3 错误描述。 */
    private final String apiMessage;
    /** 参数或业务错误明细。 */
    private final List<Detail> details;
    /** 支付宝提供的错误解决方案链接。 */
    private final List<Link> links;
    /** 支付宝链路标识。 */
    private final @Nullable String traceId;

    /**
     * 创建结构化 V3 API 异常。异常文本不包含原始响应、参数值或签名内容。
     *
     * @param statusCode HTTP 状态码
     * @param verified   错误正文是否通过响应签名验证
     * @param code        V3 错误码
     * @param apiMessage  V3 错误描述
     * @param details     参数或业务错误明细
     * @param links       错误解决方案链接
     * @param traceId     可选链路标识
     */
    public AlipayApiException(
            int statusCode,
            boolean verified,
            String code,
            String apiMessage,
            List<Detail> details,
            List<Link> links,
            @Nullable String traceId
    ) {
        super("支付宝 V3 API 调用失败，code=" + code);
        this.statusCode = statusCode;
        this.verified = verified;
        this.code = code;
        this.apiMessage = apiMessage;
        this.details = List.copyOf(details);
        this.links = List.copyOf(links);
        this.traceId = traceId;
    }

    /**
     * 获取支付宝返回的 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 判断错误正文是否通过支付宝响应签名验证。
     *
     * @return 已验证时返回 {@code true}
     */
    public boolean verified() {
        return verified;
    }

    /**
     * 获取支付宝 V3 错误码。
     *
     * @return V3 错误码
     */
    public String code() {
        return code;
    }

    /**
     * 获取支付宝 V3 错误描述。
     *
     * @return V3 错误描述
     */
    public String apiMessage() {
        return apiMessage;
    }

    /**
     * 获取参数或业务错误明细。
     *
     * @return 不可变错误明细
     */
    public List<Detail> details() {
        return details;
    }

    /**
     * 获取支付宝提供的错误解决方案链接。
     *
     * @return 不可变链接列表
     */
    public List<Link> links() {
        return links;
    }

    /**
     * 获取支付宝链路标识。
     *
     * @return 支付宝链路标识；没有时为 {@code null}
     */
    public @Nullable String traceId() {
        return traceId;
    }

    /**
     * V3 错误响应中的单项字段明细。
     *
     * @param field       出错字段；没有时为 {@code null}
     * @param value       收到的字段值；没有时为 {@code null}
     * @param location    字段位置；没有时为 {@code null}
     * @param issue       问题类型；没有时为 {@code null}
     * @param description 问题描述；没有时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            @Nullable String field,
            @Nullable String value,
            @Nullable String location,
            @Nullable String issue,
            @Nullable String description
    ) {
    }

    /**
     * V3 错误响应中的解决方案链接。
     *
     * @param link     链接地址；没有时为 {@code null}
     * @param relation 链接用途；没有时为 {@code null}
     */
    public record Link(@Nullable String link, @Nullable String relation) {
    }
}
