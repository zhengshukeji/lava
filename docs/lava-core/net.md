# 客户端 IP

应用部署在 Nginx、负载均衡或 CDN 后面时，`getRemoteAddr()` 拿到的是最近一层代理的地址，真实客户端要从 `X-Forwarded-For` 里找。`ClientIpResolver` 按代理链从右往左解析，只读取受信任代理写入的那部分，客户端自己伪造的值不会被采用。

## 基本用法

```java
// 客户端 → CDN → Nginx → 应用
ClientIpResolver resolver = ClientIpResolver.builder()
        // Nginx 在内网段内，默认信任；CDN 节点是公网地址，需要额外跳过一层
        .trustedHops(1)
        .build();

// 无法可靠识别时返回 null
String clientIp = resolver.resolve(request.getRemoteAddr(), request::getHeader);
```

解析器不可变、线程安全，应在应用内长期复用，例如在 Spring 中注册为单例 Bean。模块不依赖 Servlet，调用方传入直连地址和按名称取请求头的函数（Servlet 下即 `request::getHeader`）；同名头有多行时，函数应按逗号拼接后返回。

## 解析规则

每一层代理都会把它看到的上一跳追加到 `X-Forwarded-For` 末尾，所以越靠右的值越可信，最左边的值完全由客户端填写。解析时把直连地址接在链尾，再从右往左：

1. 位于受信任网段内的地址直接跳过。
2. 其余地址先用来抵扣 `trustedHops`，即受信任的公网代理层数。
3. 层数用完后遇到的第一个地址就是客户端，更左边的值不再读取。
4. 整条链都被跳过时，返回最左边的地址。

以「客户端 → CDN → Nginx → 应用」为例，客户端伪造了 `X-Forwarded-For: 8.8.8.8`：

| 位置 | 值 | 处理 |
| --- | --- | --- |
| 直连地址 | `127.0.0.1`（Nginx） | 内网段，跳过 |
| 末尾 | `198.51.100.20`（CDN 节点，Nginx 追加） | 抵扣 1 层 |
| 倒数第二 | `203.0.113.7`（CDN 追加） | 客户端 |
| 最左 | `8.8.8.8`（客户端伪造） | 不读取 |

## 受信任网段

默认信任 `ClientIpResolver.PRIVATE_NETWORKS`：

| 网段 | 说明 |
| --- | --- |
| `127.0.0.0/8`、`::1/128` | 回环 |
| `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16` | RFC 1918 内网，含 Docker 网桥 |
| `100.64.0.0/10` | 运营商级 NAT，阿里云 SLB 等云负载均衡回源常用 |
| `169.254.0.0/16`、`fe80::/10` | 链路本地 |
| `fc00::/7` | IPv6 唯一本地地址 |

`trustedProxies(...)` 会整体替换默认值。要在内网段之外再加网段时，把默认值一并传入：

```java
List<String> trusted = new ArrayList<>(ClientIpResolver.PRIVATE_NETWORKS);
trusted.add("198.51.100.0/24");

ClientIpResolver resolver = ClientIpResolver.builder()
        .trustedProxies(trusted)
        .build();
```

网段写法为 `地址/前缀`，也可以只写单个地址。非法网段在构建时抛出 `IllegalArgumentException`。

## CDN 专用请求头

阿里云 ESA（`ali-real-client-ip`）、Cloudflare（`CF-Connecting-IP`）等 CDN 会在边缘节点按 TCP 连接把客户端 IP 写进专用请求头。它只有一个值，不受分层回源等层数变化影响，比按 `trustedHops` 数跳数可靠。

```java
ClientIpResolver resolver = ClientIpResolver.builder()
        // 头名由调用方传入，库不绑定任何 CDN
        .clientIpHeader("ali-real-client-ip")
        .build();

String clientIp = resolver.resolve(request.getRemoteAddr(), request::getHeader);
```

配置了 `clientIpHeader` 时的处理顺序：

1. 直连方是代理（直连地址在受信任网段内，或配置了 `trustedHops`）时，读取配置的头；公网客户端直连时自己带的头不读取。
2. 头存在且是合法 IP，规范化后返回；头存在但不是合法 IP，返回 `null`，不退回 `X-Forwarded-For`。
3. 头不存在或为空白时，按上文规则解析 `X-Forwarded-For`。

::: warning 防伪造靠部署
第 1 步只看直连地址，分辨不出请求是否真的经过了 CDN。源站前面有 Nginx 时直连方总是 Nginx，绕过 CDN 直连 Nginx 的请求带上这个头同样会被采信。必须在防火墙、安全组或 Nginx 上只放行 CDN 回源地址。
:::

## 地址格式

- 只接受 IP 字面量，不做 DNS 解析；`unknown`、主机名等值视为无法识别。
- 兼容 `203.0.113.7:51234`、`[2001:db8::1]:443` 这类带端口的写法，以及带区域 ID 的 IPv6。
- IPv4 映射的 IPv6 地址（`::ffff:203.0.113.7`）按 IPv4 处理和返回。
- 返回值是 `InetAddress.getHostAddress()` 的规范化文本，IPv6 为不压缩的完整写法。

::: warning 边界
`trustedHops` 只在所有流量都必须经过这些代理时成立。如果源站可以绕过 CDN 直接访问，直连者自己占了 CDN 的位置，被跳过之后取到的就是它伪造的值。这类部署应在源站只放行 CDN 回源地址。

应当返回的那一跳不是合法 IP 时，`resolve` 返回 `null`，不会退而使用更不可信的值。
:::
