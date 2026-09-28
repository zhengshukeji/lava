/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.zhengshuyun.lava.pay.alipay.internal;

import com.zhengshuyun.lava.http.HttpUrlBuilder;
import com.zhengshuyun.lava.json.JsonCodec;
import com.zhengshuyun.lava.json.JsonException;
import com.zhengshuyun.lava.pay.alipay.exception.AlipayProtocolException;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 电脑网站支付 AOP 页面跳转数据生成器。
 *
 * <p>支付宝官方尚未提供 {@code alipay.trade.page.pay} 的 REST V3 路径；该类隔离页面支付仍需使用的
 * {@code gateway.do} 参数签名协议，避免它与服务端 API 的 V3 传输逻辑混在一起。POST 模式生成自动提交
 * HTML 表单，GET 模式生成可直接交给浏览器跳转的支付 URL；支付宝官方推荐优先使用 POST。</p>
 */
public final class AlipayPagePayRedirectFactory {
    /** 支付宝 {@code pageRedirectionData} 允许的最大长度，单位为字符。 */
    private static final int MAX_REDIRECTION_DATA_LENGTH = 16_384;
    /** 页面支付 AOP 协议版本，固定为 {@code 1.0}。 */
    private static final String VERSION = "1.0";
    /** 页面支付业务参数格式，固定为 JSON。 */
    private static final String FORMAT = "json";
    /** 页面支付签名和表单字符集，固定为 UTF-8。 */
    private static final String CHARSET = "UTF-8";
    /** 页面支付 AOP 公共参数使用的本地时间格式。 */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            Locale.ROOT
    );

    /** 支付宝应用 ID。 */
    private final String appId;
    /** 用于页面支付参数签名的应用私钥。 */
    private final PrivateKey appPrivateKey;
    /** 用于生成 {@code gateway.do} 地址的 OpenAPI 基础地址。 */
    private final URI baseUrl;
    /** 生成 AOP 时间戳的协议时钟。 */
    private final Clock clock;
    /** 编码 {@code biz_content} 的 JSON 编解码器。 */
    private final JsonCodec jsonCodec;

    /**
     * 创建页面支付跳转数据生成器。
     *
     * @param appId         支付宝应用 ID
     * @param appPrivateKey 应用私钥
     * @param baseUrl       OpenAPI 基础地址
     * @param clock         协议时钟
     * @param jsonCodec     JSON 编解码器
     */
    public AlipayPagePayRedirectFactory(
            String appId,
            PrivateKey appPrivateKey,
            URI baseUrl,
            Clock clock,
            JsonCodec jsonCodec
    ) {
        this.appId = appId;
        this.appPrivateKey = appPrivateKey;
        this.baseUrl = baseUrl;
        this.clock = clock;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 生成电脑网站支付的官方 AOP 签名 POST 表单，不向支付宝发送 HTTP 请求。
     *
     * @param method     页面支付接口名称
     * @param bizRequest 业务参数
     * @param notifyUrl  异步通知地址
     * @param returnUrl  同步返回地址
     * @return 可直接作为 HTML 响应输出的自动提交表单
     */
    public String createForm(
            String method,
            Object bizRequest,
            URI notifyUrl,
            URI returnUrl
    ) {
        // 1. 业务对象只编码一次，保证参与签名和写入隐藏字段的是同一份 JSON 原文。
        String bizContent = encode(bizRequest);

        // 2. 注入 AOP 公共参数、回调地址和时间戳，再按参数名字典序生成 RSA2 签名。
        Map<String, String> signed = signedParameters(
                method,
                bizContent,
                notifyUrl,
                returnUrl
        );
        Map<String, String> query = new LinkedHashMap<>(signed);
        query.remove("biz_content");

        // 3. 公共参数放入 gateway.do 查询串，biz_content 单独放入 POST 隐藏字段。
        HttpUrlBuilder url = HttpUrlBuilder.from(baseUrl).encodedPath("/gateway.do");
        query.forEach(url::queryParam);
        String action = htmlEscape(url.build().toASCIIString());
        String hiddenValue = htmlEscape(bizContent);

        // 4. 在输出 HTML 属性前完成转义，阻止业务文本破坏自动提交表单结构。
        return "<form name=\"punchout_form\" method=\"post\" action=\"" + action + "\">\n"
                + "<input type=\"hidden\" name=\"biz_content\" value=\"" + hiddenValue
                + "\">\n<input type=\"submit\" value=\"立即支付\" style=\"display:none\">\n"
                + "</form>\n<script>document.forms[0].submit();</script>";
    }

    /**
     * 生成电脑网站支付的官方 AOP 签名 GET 地址，不向支付宝发送 HTTP 请求。
     *
     * <p>该地址包含完整业务参数和签名，可能出现在浏览器历史、代理及访问日志中；调用方不得记录
     * 完整地址。业务参数较多时应改用 {@link #createForm(String, Object, URI, URI)}，避免超过浏览器、
     * 网关或支付宝允许的 URL 长度。</p>
     *
     * @param method     页面支付接口名称
     * @param bizRequest 业务参数
     * @param notifyUrl  异步通知地址
     * @param returnUrl  同步返回地址
     * @return 可直接交给浏览器打开或作为 HTTP 重定向目标的支付宝支付地址
     */
    public URI createUrl(
            String method,
            Object bizRequest,
            URI notifyUrl,
            URI returnUrl
    ) {
        // 1. 业务对象只编码一次，保证参与签名和进入查询参数的是同一份 JSON 原文。
        String bizContent = encode(bizRequest);

        // 2. 对全部 AOP 公共参数、回调地址和业务参数生成 RSA2 签名。
        Map<String, String> signed = signedParameters(
                method,
                bizContent,
                notifyUrl,
                returnUrl
        );

        // 3. 将签名前的原始参数逐项编码为查询参数，返回浏览器可直接打开的 GET 地址。
        HttpUrlBuilder url = HttpUrlBuilder.from(baseUrl).encodedPath("/gateway.do");
        signed.forEach(url::queryParam);
        URI result = url.build();
        if (result.toASCIIString().length() > MAX_REDIRECTION_DATA_LENGTH) {
            throw new AlipayProtocolException(
                    "支付宝 GET 页面跳转地址超过 16384 字符，请改用 POST 表单"
            );
        }
        return result;
    }

    /**
     * 按 AOP 参数名字典序构造签名参数。
     *
     * @param method     接口名称
     * @param bizContent JSON 业务参数原文
     * @param notifyUrl  可选异步通知地址
     * @param returnUrl  可选同步返回地址
     * @return 包含签名的参数集合
     */
    private Map<String, String> signedParameters(
            String method,
            String bizContent,
            @Nullable URI notifyUrl,
            @Nullable URI returnUrl
    ) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("biz_content", bizContent);
        params.put("charset", CHARSET);
        params.put("format", FORMAT);
        params.put("method", method);
        if (notifyUrl != null) {
            params.put("notify_url", notifyUrl.toASCIIString());
        }
        if (returnUrl != null) {
            params.put("return_url", returnUrl.toASCIIString());
        }
        params.put("sign_type", AlipayCryptoUtils.SIGN_TYPE);
        params.put("timestamp", TIMESTAMP.format(
                clock.instant().atZone(ZoneOffset.ofHours(8))));
        params.put("version", VERSION);
        params.put("sign", AlipayCryptoUtils.sign(params, appPrivateKey));
        return params;
    }

    /**
     * 编码页面支付 {@code biz_content}。
     *
     * @param value 页面支付请求模型
     * @return JSON 原文
     */
    private String encode(Object value) {
        try {
            return jsonCodec.write(value);
        } catch (JsonException exception) {
            throw new AlipayProtocolException("无法编码支付宝页面支付请求 JSON", exception);
        }
    }

    /**
     * 转义写入 HTML 属性的内容，阻止业务文本破坏表单结构。
     *
     * @param value 原始属性值
     * @return HTML 安全属性值
     */
    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
