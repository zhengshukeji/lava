/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.bill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhengshuyun.lava.core.lang.ValidationUtils;
import com.zhengshuyun.lava.http.HttpMethod;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import com.zhengshuyun.lava.pay.alipay.internal.AlipayTransport;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝 OpenAPI V3 对账单下载地址查询客户端。
 */
public final class BillClient {
    /** 查询对账单下载地址的 OpenAPI V3 固定路径。 */
    private static final String QUERY_PATH =
            "/v3/alipay/data/dataservice/bill/downloadurl/query";

    /** 根客户端共享的 V3 传输层和关闭状态。 */
    private final AlipayTransport transport;

    /**
     * 由根客户端创建账单入口。
     *
     * @param transport 共享协议传输层
     */
    public BillClient(AlipayTransport transport) {
        this.transport = ValidationUtils.requireNonNull(transport, "transport");
    }

    /**
     * 查询日账单下载地址。
     *
     * @param billType 账单类型
     * @param date     账单日期
     * @return 已验签下载信息
     */
    public BillDownloadInfo queryDaily(String billType, LocalDate date) {
        return query(BillRequest.builder().billType(billType).date(date).build());
    }

    /**
     * 查询月账单下载地址。
     *
     * @param billType 账单类型
     * @param month    账单月份
     * @return 已验签下载信息
     */
    public BillDownloadInfo queryMonthly(String billType, YearMonth month) {
        return query(BillRequest.builder().billType(billType).month(month).build());
    }

    /**
     * 使用完整参数查询账单下载地址。
     *
     * @param request 查询参数
     * @return 已验签下载信息
     */
    public BillDownloadInfo query(BillRequest request) {
        // 1. 按支付宝业务时区校验账单日期，并构造最终参与 V3 签名的查询参数。
        transport.ensureOpen();
        ValidationUtils.requireNonNull(request, "request must not be null");
        String billDate = request.date() == null
                ? request.month().toString() : request.date().toString();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("bill_type", request.billType());
        query.put("bill_date", billDate);
        if (request.smid() != null) {
            query.put("smid", request.smid());
        }
        // 2. 由传输层发送 GET 空正文请求，并在解析前验证支付宝原始响应签名。
        Payload response = transport.execute(
                QUERY_PATH,
                HttpMethod.GET,
                null,
                query,
                Payload.class
        );
        // 3. 严格验证下载地址结构，确保响应至少包含下载地址或官方文件状态。
        URI downloadUrl = parseUrl(response.downloadUrl);
        if (downloadUrl == null && response.fileCode == null) {
            throw new AlipayProtocolException(
                    "支付宝账单响应未返回下载地址或文件状态");
        }
        return new BillDownloadInfo(downloadUrl, response.fileCode);
    }

    /**
     * 解析并校验支付宝返回的账单下载地址。
     *
     * @param value 可选下载地址文本
     * @return 合法 URI；未返回时为 {@code null}
     */
    private static @Nullable URI parseUrl(@Nullable String value) {
        if (value == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new AlipayProtocolException("支付宝账单下载地址格式无效", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new AlipayProtocolException("支付宝账单下载地址格式无效");
        }
        return uri;
    }

    /**
     * 对账单下载地址接口的原始响应载荷，验签后才允许映射为公开模型。
     *
     * @param downloadUrl 临时下载地址，最长 2048 字符；没有可下载账单时可能为 {@code null}
     * @param fileCode    账单文件状态码；支付宝未返回时为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(
            @JsonProperty("bill_download_url") @Nullable String downloadUrl,
            @JsonProperty("bill_file_code") @Nullable String fileCode) {
    }
}
