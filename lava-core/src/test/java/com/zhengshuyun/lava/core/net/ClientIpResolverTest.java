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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private final ClientIpResolver direct = ClientIpResolver.builder().build();

    private final ClientIpResolver behindCdn = ClientIpResolver.builder()
            .trustedHops(1)
            .build();

    /**
     * 只带 X-Forwarded-For 的取头函数；传 null 表示没有该头。
     */
    private static Function<String, @Nullable String> xff(@Nullable String value) {
        return name -> "X-Forwarded-For".equals(name) ? value : null;
    }

    @Test
    void returnsRemoteAddressWhenItIsPublic() {
        assertEquals("203.0.113.7", direct.resolve("203.0.113.7", xff("198.51.100.1")));
    }

    @Test
    void skipsPrivateProxiesFromTheRight() {
        // 客户端 → Nginx(127.0.0.1) → 应用
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", xff("203.0.113.7")));
        // 客户端 → SLB(100.64.x) → Nginx(10.x) → 应用
        assertEquals("203.0.113.7", direct.resolve("10.0.0.5", xff("203.0.113.7, 100.64.1.2")));
    }

    @Test
    void ignoresSpoofedValuesLeftOfTheClient() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", xff("1.1.1.1, 10.0.0.1, 203.0.113.7")));
    }

    @Test
    void skipsConfiguredPublicHopsForCdn() {
        // 客户端 → CDN(198.51.100.20) → Nginx(127.0.0.1) → 应用，客户端自己伪造了一段
        assertEquals("203.0.113.7", behindCdn.resolve("127.0.0.1", xff("8.8.8.8, 203.0.113.7, 198.51.100.20")));
        // CDN 后面还有一层内网 SLB
        assertEquals("203.0.113.7", behindCdn.resolve("127.0.0.1", xff("203.0.113.7, 198.51.100.20, 100.64.0.9")));
    }

    @Test
    void returnsLeftmostAddressWhenWholeChainIsSkipped() {
        assertEquals("127.0.0.1", direct.resolve("127.0.0.1", xff(null)));
        assertEquals("192.168.1.10", behindCdn.resolve("127.0.0.1", xff("192.168.1.10")));
        assertEquals("198.51.100.20", behindCdn.resolve("127.0.0.1", xff("198.51.100.20")));
    }

    @Test
    void returnsNullWhenTheClientHopIsNotAnIpLiteral() {
        assertNull(direct.resolve("127.0.0.1", xff("unknown")));
        assertNull(behindCdn.resolve("127.0.0.1", xff("203.0.113.7, cdn.example.com")));
    }

    @Test
    void doesNotReadInvalidValuesLeftOfTheClient() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", xff("garbage, 203.0.113.7")));
    }

    @Test
    void acceptsPortsBracketsAndZones() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", xff("203.0.113.7:51234")));
        assertEquals("2001:db8:0:0:0:0:0:1", direct.resolve("127.0.0.1", xff("[2001:db8::1]:443")));
        assertEquals("2001:db8:0:0:0:0:0:1", direct.resolve("::1", xff("2001:db8::1")));
        assertEquals("fe80:0:0:0:0:0:0:1", direct.resolve("fe80::1%eth0", xff(null)));
    }

    @Test
    void treatsIpv4MappedIpv6AsIpv4() {
        assertEquals("203.0.113.7", direct.resolve("::ffff:127.0.0.1", xff("::ffff:203.0.113.7")));
    }

    @Test
    void skipsBlankSegmentsAndWhitespace() {
        assertEquals("203.0.113.7", direct.resolve(" 127.0.0.1 ", xff(" , 203.0.113.7 ,, ")));
    }

    @Test
    void customTrustedProxiesReplaceDefaults() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies("198.51.100.0/24")
                .build();

        // 127.0.0.1 不再受信任，直接作为客户端
        assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", xff("203.0.113.7")));
        assertEquals("203.0.113.7", resolver.resolve("198.51.100.20", xff("203.0.113.7")));
    }

    @Test
    void matchesPrefixesThatDoNotEndOnByteBoundary() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies("100.64.0.0/10", "::ffff:198.51.100.0/120")
                .build();

        assertEquals("203.0.113.7", resolver.resolve("100.127.255.255", xff("203.0.113.7")));
        assertEquals("100.128.0.1", resolver.resolve("100.128.0.1", xff("203.0.113.7")));
        assertEquals("203.0.113.7", resolver.resolve("198.51.100.200", xff("203.0.113.7")));
    }

    @Test
    void acceptsSingleAddressAsFullLengthRange() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies(List.of("198.51.100.20"))
                .build();

        assertEquals("203.0.113.7", resolver.resolve("198.51.100.20", xff("203.0.113.7")));
        assertEquals("198.51.100.21", resolver.resolve("198.51.100.21", xff("203.0.113.7")));
    }

    @Test
    void prefersClientIpHeaderWhenRemoteIsProxy() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("ali-real-client-ip")
                .build();
        Map<String, String> headers = Map.of(
                "ali-real-client-ip", "203.0.113.7",
                "X-Forwarded-For", "8.8.8.8, 198.51.100.20, 198.51.100.21");

        // ESA → Nginx(127.0.0.1) → 应用：不受分层回源层数影响
        assertEquals("203.0.113.7", resolver.resolve("127.0.0.1", headers::get));
    }

    @Test
    void readsClientIpHeaderWhenPublicHopsAreConfigured() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("CF-Connecting-IP")
                .trustedHops(1)
                .build();

        // CDN 节点直连应用，直连地址是公网 IP，但配置了公网代理层数
        assertEquals("203.0.113.7", resolver.resolve("198.51.100.20", Map.of("CF-Connecting-IP", "203.0.113.7")::get));
    }

    @Test
    void ignoresClientIpHeaderFromDirectClient() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("ali-real-client-ip")
                .build();

        // 公网客户端直连，自己带的头不可信
        assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", Map.of("ali-real-client-ip", "8.8.8.8")::get));
    }

    @Test
    void fallsBackToForwardedForWhenClientIpHeaderIsAbsent() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("ali-real-client-ip")
                .trustedHops(1)
                .build();

        assertEquals("203.0.113.7", resolver.resolve("127.0.0.1", Map.of("X-Forwarded-For", "8.8.8.8, 203.0.113.7, 198.51.100.20")::get));
        assertEquals("203.0.113.7", resolver.resolve("127.0.0.1", Map.of(
                "ali-real-client-ip", " ",
                "X-Forwarded-For", "203.0.113.7, 198.51.100.20")::get));
    }

    @Test
    void returnsNullWhenClientIpHeaderIsInvalid() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("ali-real-client-ip")
                .build();

        // 不退回更不可信的 X-Forwarded-For
        assertNull(resolver.resolve("127.0.0.1", Map.of(
                "ali-real-client-ip", "unknown",
                "X-Forwarded-For", "203.0.113.7")::get));
    }

    @Test
    void normalizesClientIpHeaderValue() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .clientIpHeader("ali-real-client-ip")
                .build();

        assertEquals("2001:db8:0:0:0:0:0:1", resolver.resolve("127.0.0.1", Map.of("ali-real-client-ip", " 2001:db8::1 ")::get));
        assertEquals("203.0.113.7", resolver.resolve("127.0.0.1", Map.of("ali-real-client-ip", "::ffff:203.0.113.7")::get));
    }

    @Test
    void readsForwardedForWithoutClientIpHeader() {
        assertEquals("203.0.113.7", behindCdn.resolve("127.0.0.1", Map.of("X-Forwarded-For", "203.0.113.7, 198.51.100.20")::get));
        assertEquals("127.0.0.1", direct.resolve("127.0.0.1", Map.<String, String>of()::get));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("example.com/8"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies(" "));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedHops(-1));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().clientIpHeader(" "));
    }
}
