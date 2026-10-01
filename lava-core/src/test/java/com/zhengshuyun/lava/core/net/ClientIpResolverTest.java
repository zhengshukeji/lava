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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private final ClientIpResolver direct = ClientIpResolver.builder().build();

    private final ClientIpResolver behindCdn = ClientIpResolver.builder()
            .trustedHops(1)
            .build();

    @Test
    void returnsRemoteAddressWhenItIsPublic() {
        assertEquals("203.0.113.7", direct.resolve("203.0.113.7", "198.51.100.1"));
    }

    @Test
    void skipsPrivateProxiesFromTheRight() {
        // 客户端 → Nginx(127.0.0.1) → 应用
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", "203.0.113.7"));
        // 客户端 → SLB(100.64.x) → Nginx(10.x) → 应用
        assertEquals("203.0.113.7", direct.resolve("10.0.0.5", "203.0.113.7, 100.64.1.2"));
    }

    @Test
    void ignoresSpoofedValuesLeftOfTheClient() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", "1.1.1.1, 10.0.0.1, 203.0.113.7"));
    }

    @Test
    void skipsConfiguredPublicHopsForCdn() {
        // 客户端 → CDN(198.51.100.20) → Nginx(127.0.0.1) → 应用，客户端自己伪造了一段
        assertEquals("203.0.113.7", behindCdn.resolve("127.0.0.1", "8.8.8.8, 203.0.113.7, 198.51.100.20"));
        // CDN 后面还有一层内网 SLB
        assertEquals("203.0.113.7", behindCdn.resolve("127.0.0.1", "203.0.113.7, 198.51.100.20, 100.64.0.9"));
    }

    @Test
    void returnsLeftmostAddressWhenWholeChainIsSkipped() {
        assertEquals("127.0.0.1", direct.resolve("127.0.0.1", null));
        assertEquals("192.168.1.10", behindCdn.resolve("127.0.0.1", "192.168.1.10"));
        assertEquals("198.51.100.20", behindCdn.resolve("127.0.0.1", "198.51.100.20"));
    }

    @Test
    void returnsNullWhenTheClientHopIsNotAnIpLiteral() {
        assertNull(direct.resolve("127.0.0.1", "unknown"));
        assertNull(behindCdn.resolve("127.0.0.1", "203.0.113.7, cdn.example.com"));
    }

    @Test
    void doesNotReadInvalidValuesLeftOfTheClient() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", "garbage, 203.0.113.7"));
    }

    @Test
    void acceptsPortsBracketsAndZones() {
        assertEquals("203.0.113.7", direct.resolve("127.0.0.1", "203.0.113.7:51234"));
        assertEquals("2001:db8:0:0:0:0:0:1", direct.resolve("127.0.0.1", "[2001:db8::1]:443"));
        assertEquals("2001:db8:0:0:0:0:0:1", direct.resolve("::1", "2001:db8::1"));
        assertEquals("fe80:0:0:0:0:0:0:1", direct.resolve("fe80::1%eth0", null));
    }

    @Test
    void treatsIpv4MappedIpv6AsIpv4() {
        assertEquals("203.0.113.7", direct.resolve("::ffff:127.0.0.1", "::ffff:203.0.113.7"));
    }

    @Test
    void skipsBlankSegmentsAndWhitespace() {
        assertEquals("203.0.113.7", direct.resolve(" 127.0.0.1 ", " , 203.0.113.7 ,, "));
    }

    @Test
    void customTrustedProxiesReplaceDefaults() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies("198.51.100.0/24")
                .build();

        // 127.0.0.1 不再受信任，直接作为客户端
        assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", "203.0.113.7"));
        assertEquals("203.0.113.7", resolver.resolve("198.51.100.20", "203.0.113.7"));
    }

    @Test
    void matchesPrefixesThatDoNotEndOnByteBoundary() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies("100.64.0.0/10", "::ffff:198.51.100.0/120")
                .build();

        assertEquals("203.0.113.7", resolver.resolve("100.127.255.255", "203.0.113.7"));
        assertEquals("100.128.0.1", resolver.resolve("100.128.0.1", "203.0.113.7"));
        assertEquals("203.0.113.7", resolver.resolve("198.51.100.200", "203.0.113.7"));
    }

    @Test
    void acceptsSingleAddressAsFullLengthRange() {
        ClientIpResolver resolver = ClientIpResolver.builder()
                .trustedProxies(List.of("198.51.100.20"))
                .build();

        assertEquals("203.0.113.7", resolver.resolve("198.51.100.20", "203.0.113.7"));
        assertEquals("198.51.100.21", resolver.resolve("198.51.100.21", "203.0.113.7"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("example.com/8"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedProxies(" "));
        assertThrows(IllegalArgumentException.class, () -> ClientIpResolver.builder().trustedHops(-1));
    }
}
