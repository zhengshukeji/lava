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

package com.zhengshuyun.lava.pay.wechat.internal;

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 微信支付参数校验。
 *
 * <p>只校验必填项和本 SDK 无法正确发出请求的情况；字段格式、长度等业务规则由微信支付服务端
 * 校验并返回明确错误码，客户端不重复实现，避免平台规则调整后两边不一致。</p>
 */
public final class WechatPayValidationUtils {

    /**
     * 微信支付官方 API 主、备域名；baseUrl 白名单与账单下载地址信任边界共用这一份，避免两处分叉。
     */
    static final Set<String> OFFICIAL_API_HOSTS = Set.of("api.mch.weixin.qq.com", "api2.mch.weixin.qq.com");

    private WechatPayValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验商户号非空白。
     *
     * @param value 商户号
     * @return 原值
     */
    public static String requireMchid(String value) {
        return ValidationUtils.requireNotBlank(value, "mchid must not be blank");
    }

    /**
     * 校验应用 ID 非空白。
     *
     * @param value 应用 ID
     * @return 原值
     */
    public static String requireAppid(String value) {
        return ValidationUtils.requireNotBlank(value, "appid must not be blank");
    }

    /**
     * 校验商户订单号非空白。
     *
     * @param value 商户订单号
     * @return 原值
     */
    public static String requireOutTradeNo(String value) {
        return ValidationUtils.requireNotBlank(value, "outTradeNo must not be blank");
    }

    /**
     * 校验商户退款单号非空白。
     *
     * @param value 商户退款单号
     * @return 原值
     */
    public static String requireOutRefundNo(String value) {
        return ValidationUtils.requireNotBlank(value, "outRefundNo must not be blank");
    }

    /**
     * 校验商户侧商品编码非空白。
     *
     * @param value 商品编码
     * @return 原值
     */
    public static String requireMerchantGoodsId(String value) {
        return ValidationUtils.requireNotBlank(value, "merchantGoodsId must not be blank");
    }

    /**
     * 校验 IP 地址非空白；格式由服务端校验。
     *
     * @param value IP 地址
     * @param name  参数名称
     * @return 原值
     */
    public static String requireIpAddress(String value, String name) {
        return ValidationUtils.requireNotBlank(value, name + " must not be blank");
    }

    /**
     * 校验金额、数量等数值为正数。
     *
     * @param value 数值
     * @param name  参数名称
     * @return 原值
     */
    public static long requirePositive(long value, String name) {
        ValidationUtils.requireTrue(value > 0, name + " must be positive");
        return value;
    }

    /**
     * 校验支付通知地址：微信支付只能回调绝对 HTTPS 地址。
     *
     * @param value 通知地址
     * @return 原值
     */
    public static URI requireNotifyUrl(URI value) {
        ValidationUtils.requireNonNull(value, "notifyUrl must not be null");
        ValidationUtils.requireTrue(value.isAbsolute() && "https".equalsIgnoreCase(value.getScheme())
                        && value.getHost() != null,
                "notifyUrl must be an absolute HTTPS URL");
        return value;
    }

    /**
     * 解析并校验支付通知地址。
     *
     * @param value 通知地址文本
     * @return 解析后的地址
     */
    public static URI requireNotifyUrl(String value) {
        ValidationUtils.requireNotBlank(value, "notifyUrl must not be blank");
        try {
            return requireNotifyUrl(new URI(value));
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("notifyUrl must be a valid URI", exception);
        }
    }

    /**
     * 校验 API 根地址并统一补齐末尾斜杠。
     *
     * <p>APIv3 签名不覆盖主机名，签名请求一旦发往第三方主机即可在有效期内被中继到真实接口，
     * 因此只允许官方主、备域名（与官方 wechatpay-java 的 {@code HostName} 限制一致）；环回主机
     * 仅用于本地协议测试。根地址不得带路径、查询参数、片段或用户信息，路径由传输层拼接。</p>
     *
     * @param value API 根地址
     * @return 以斜杠结尾的根地址
     */
    public static URI requireApiBaseUrl(URI value) {
        String name = "apiBaseUrl";
        ValidationUtils.requireNonNull(value, name + " must not be null");
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        String host = value.getHost() == null ? "" : value.getHost().toLowerCase(Locale.ROOT);
        boolean official = scheme.equals("https") && OFFICIAL_API_HOSTS.contains(host)
                && (value.getPort() == -1 || value.getPort() == 443);
        boolean localTest = isLoopback(host) && (scheme.equals("https") || scheme.equals("http"));
        ValidationUtils.requireTrue(value.isAbsolute() && (official || localTest),
                name + " must use an official WeChat Pay HTTPS host or a loopback test host");
        ValidationUtils.requireTrue(value.getRawQuery() == null && value.getRawFragment() == null
                        && value.getUserInfo() == null,
                name + " must not contain user information, query, or fragment");
        ValidationUtils.requireTrue(value.getRawPath() == null || value.getRawPath().isEmpty()
                        || "/".equals(value.getRawPath()),
                name + " must not contain a path");
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + '/');
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || host.startsWith("127.")
                || "[::1]".equals(host) || "::1".equals(host);
    }
}
