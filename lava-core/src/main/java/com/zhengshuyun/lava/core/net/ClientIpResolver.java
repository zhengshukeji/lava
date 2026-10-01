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

package com.zhengshuyun.lava.core.net;

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 从直连地址和 {@code X-Forwarded-For} 中解析真实客户端 IP，不可变且线程安全。
 *
 * <pre>{@code
 * // 客户端 → CDN → Nginx → 应用：Nginx 在内网段内自动信任，CDN 是一层需要额外跳过的公网代理
 * ClientIpResolver resolver = ClientIpResolver.builder()
 *         .trustedHops(1)
 *         .build();
 *
 * String clientIp = resolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
 * }</pre>
 *
 * <p>{@code X-Forwarded-For} 的每一跳代理都把它看到的上一跳追加到末尾，最左边的值由客户端
 * 自己填写、可以任意伪造。因此解析从右往左进行：把直连地址接在链尾，依次跳过受信任网段内的
 * 代理（默认是回环、内网、链路本地和运营商级 NAT 地址），再跳过 {@code trustedHops} 个公网代理
 * （如 CDN 节点），遇到的第一个其余地址就是客户端。整条链都被跳过时返回最左边的地址。</p>
 *
 * <p>{@code trustedHops} 只在所有流量都必须经过这些代理时成立：如果源站可以被绕过 CDN 直连，
 * 直连者自己就占了 CDN 的位置，被跳过之后取到的是它伪造的值。这类部署应在源站只放行 CDN 回源。</p>
 *
 * <p>只接受 IP 字面量，不做 DNS 解析；IPv6 支持方括号和端口写法，IPv4 支持端口写法，IPv4 映射的
 * IPv6 地址按 IPv4 处理。</p>
 */
public final class ClientIpResolver {

    /**
     * 默认信任的网段：回环、RFC 1918 内网、链路本地、运营商级 NAT（云负载均衡常用）和 IPv6 唯一本地地址。
     */
    public static final List<String> PRIVATE_NETWORKS = List.of(
            "127.0.0.0/8",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "100.64.0.0/10",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
    );

    /**
     * 受信任代理所在的网段；位于其中的地址总是被跳过，不消耗 {@link #trustedHops}。
     */
    private final List<IpRange> trustedProxies;

    /**
     * 跳过受信任网段后，还要再跳过的公网代理层数。
     */
    private final int trustedHops;

    /**
     * 按构建器的配置创建解析器。
     *
     * @param builder 已校验的构建器
     */
    private ClientIpResolver(Builder builder) {
        this.trustedProxies = List.copyOf(builder.trustedProxies);
        this.trustedHops = builder.trustedHops;
    }

    /**
     * 创建构建器：默认信任 {@link #PRIVATE_NETWORKS}，不额外跳过公网代理。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 解析客户端 IP。
     *
     * @param remoteAddress 当前连接的对端地址，如 Servlet 的 {@code getRemoteAddr()}
     * @param forwardedFor  {@code X-Forwarded-For} 的值；多行时按逗号拼接后传入，没有时传 {@code null}
     * @return 规范化后的客户端 IP 文本；应当返回的那一跳不是合法 IP 字面量时返回 {@code null}
     */
    public @Nullable String resolve(String remoteAddress, @Nullable String forwardedFor) {
        ValidationUtils.requireNonNull(remoteAddress, "remoteAddress must not be null");
        List<String> chain = new ArrayList<>();
        if (forwardedFor != null) {
            for (String hop : forwardedFor.split(",")) {
                if (!hop.isBlank()) {
                    chain.add(hop.strip());
                }
            }
        }
        chain.add(remoteAddress.strip());

        // 1. 从直连地址开始往左走：受信任网段内的代理直接跳过，其余地址先消耗公网代理层数。
        // 2. 层数耗尽后遇到的第一个非受信任地址就是客户端；更左边的值可能是客户端伪造的，不再读取。
        int hopsLeft = trustedHops;
        for (int i = chain.size() - 1; i >= 0; i--) {
            InetAddress address = parseLiteral(chain.get(i));
            if (address == null) {
                // 一跳无法识别时，无论它处在代理段还是客户端位置，都无法再可靠地推断客户端
                return null;
            }
            if (i == 0) {
                return address.getHostAddress();
            }
            if (isTrusted(address)) {
                continue;
            }
            if (hopsLeft > 0) {
                hopsLeft--;
                continue;
            }
            return address.getHostAddress();
        }
        // 链至少包含直连地址，循环一定在 i == 0 时返回
        throw new IllegalStateException("unreachable");
    }

    /**
     * 判断地址是否位于受信任代理网段。
     *
     * @param address 待判断地址
     * @return 位于任一受信任网段时为 {@code true}
     */
    private boolean isTrusted(InetAddress address) {
        for (IpRange range : trustedProxies) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把一跳文本解析为 IP；兼容 {@code [v6]:port}、{@code [v6]}、{@code v4:port} 与带区域 ID 的 IPv6。
     *
     * @param text 一跳的原始文本
     * @return 解析出的地址；不是 IP 字面量（如 {@code unknown}、主机名）时为 {@code null}
     */
    private static @Nullable InetAddress parseLiteral(String text) {
        String literal = text;
        if (literal.startsWith("[")) {
            int end = literal.indexOf(']');
            if (end < 0) {
                return null;
            }
            literal = literal.substring(1, end);
        } else if (literal.indexOf(':') >= 0 && literal.indexOf(':') == literal.lastIndexOf(':')) {
            // 只有一个冒号的只能是 IPv4 加端口；IPv6 至少有两个冒号
            literal = literal.substring(0, literal.indexOf(':'));
        }
        int zone = literal.indexOf('%');
        if (zone >= 0) {
            literal = literal.substring(0, zone);
        }
        try {
            // ofLiteral 只接受字面量，主机名直接报错，不会触发 DNS 查询
            return InetAddress.ofLiteral(literal);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * CIDR 网段。
     *
     * @param network      网络地址的字节形式，IPv4 为 4 字节，IPv6 为 16 字节
     * @param prefixLength 前缀位数
     */
    private record IpRange(byte[] network, int prefixLength) {

        /**
         * 解析 {@code 地址/前缀} 或单个地址（等价于全长前缀）。
         *
         * @param cidr 网段文本
         * @return 网段
         * @throws IllegalArgumentException 文本不是合法网段
         */
        static IpRange parse(String cidr) {
            ValidationUtils.requireNotBlank(cidr, "trusted proxy must not be blank");
            String text = cidr.strip();
            int slash = text.indexOf('/');
            InetAddress address = parseLiteral(slash < 0 ? text : text.substring(0, slash));
            if (address == null) {
                throw new IllegalArgumentException("invalid trusted proxy: " + cidr);
            }
            byte[] bytes = address.getAddress();
            int maxPrefix = bytes.length * 8;
            int prefix = maxPrefix;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("invalid trusted proxy: " + cidr, exception);
                }
                // IPv4 映射写法（::ffff:a.b.c.d/n）被解析为 IPv4，前缀按 IPv6 位数换算
                if (address instanceof Inet4Address && text.indexOf(':') >= 0) {
                    prefix -= 96;
                }
            }
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalArgumentException("invalid trusted proxy prefix: " + cidr);
            }
            return new IpRange(bytes, prefix);
        }

        /**
         * 判断地址是否落在网段内；IPv4 与 IPv6 互不匹配。
         *
         * @param address 待判断地址
         * @return 落在网段内时为 {@code true}
         */
        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            if (!Arrays.equals(bytes, 0, fullBytes, network, 0, fullBytes)) {
                return false;
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (bytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    /**
     * {@link ClientIpResolver} 的构建器。
     */
    public static final class Builder {

        /**
         * 受信任代理网段。
         */
        private List<IpRange> trustedProxies = parseAll(PRIVATE_NETWORKS);

        /**
         * 额外跳过的公网代理层数。
         */
        private int trustedHops;

        /**
         * 只能通过 {@link ClientIpResolver#builder()} 创建。
         */
        private Builder() {
        }

        /**
         * 用给定网段替换默认的受信任代理网段；传空集合表示不信任任何网段。
         *
         * <p>需要在内网段之外再加网段时，把 {@link #PRIVATE_NETWORKS} 一并传入。</p>
         *
         * @param cidrs CIDR 网段或单个 IP，如 {@code 10.0.0.0/8}、{@code 203.0.113.7}
         * @return 当前构建器
         * @throws IllegalArgumentException 任一网段不合法
         */
        public Builder trustedProxies(Collection<String> cidrs) {
            ValidationUtils.requireNonNull(cidrs, "cidrs must not be null");
            this.trustedProxies = parseAll(cidrs);
            return this;
        }

        /**
         * 用给定网段替换默认的受信任代理网段。
         *
         * @param cidrs CIDR 网段或单个 IP
         * @return 当前构建器
         * @throws IllegalArgumentException 任一网段不合法
         */
        public Builder trustedProxies(String... cidrs) {
            ValidationUtils.requireNonNull(cidrs, "cidrs must not be null");
            return trustedProxies(Arrays.asList(cidrs));
        }

        /**
         * 设置跳过受信任网段后还要再跳过的公网代理层数，如前面有一层 CDN 时为 1。
         *
         * @param trustedHops 非负层数
         * @return 当前构建器
         */
        public Builder trustedHops(int trustedHops) {
            ValidationUtils.requireTrue(trustedHops >= 0, "trustedHops must be >= 0");
            this.trustedHops = trustedHops;
            return this;
        }

        /**
         * 构建解析器。
         *
         * @return 不可变的解析器
         */
        public ClientIpResolver build() {
            return new ClientIpResolver(this);
        }

        /**
         * 逐个解析网段文本。
         *
         * @param cidrs 网段文本
         * @return 网段列表
         * @throws IllegalArgumentException 任一网段不合法
         */
        private static List<IpRange> parseAll(Collection<String> cidrs) {
            List<IpRange> ranges = new ArrayList<>(cidrs.size());
            for (String cidr : cidrs) {
                ranges.add(IpRange.parse(cidr));
            }
            return ranges;
        }
    }
}
