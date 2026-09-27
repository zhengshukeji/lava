# lava-pay-wechat

`lava-pay-wechat` 是面向微信支付 APIv3 普通商户的同步 Java 工具包，不依赖微信支付官方 SDK。模块基于 `lava-http`、
`lava-json` 和 `lava-crypto`，完成请求签名、应答验签、通知解密、Native 下单、交易查询、退款和账单下载。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-pay-wechat</artifactId>
</dependency>
```

版本建议由 [`lava-bom`](../lava-bom/README.md) 管理。

## 接入边界

当前版本支持：

- APIv3 境内普通商户；
- 微信支付公钥验签模式；
- Native 下单并返回 `code_url`；
- 按商户订单号或微信支付订单号查单、关闭未支付订单；
- 正常退款申请、退款查询、支付及退款通知解析；
- 交易账单和资金账单申请、流式下载及 SHA-1 完整性校验。

当前不包含服务商模式、平台证书模式、异常退款、二维码图片渲染和账单 CSV/GZIP 解析。模块只负责微信支付协议适配，
不负责业务支付订单、幂等、渠道路由、通知持久化、轮询补偿和对账差异处理。

从平台证书切换到微信支付公钥的灰度期内，部分回调仍可能由平台证书签名。本模块会对这类回调失败关闭；存量商户应在
公钥回调切换完成后使用，或在迁移期继续保留原平台证书回调处理链路。

使用前，商户需要准备：

- 商户号 `mchid`；
- 已与商户号绑定的 `appid`；
- 商户 API 私钥及对应证书或证书序列号；
- 32 位 ASCII 字母数字 APIv3 密钥；
- 微信支付公钥 ID 和公钥文件；
- 外网可访问、无查询参数的 HTTPS 支付通知地址。

## 创建客户端

```java
import com.zhengshuyun.lava.pay.wechat.WechatPayClient;

import java.nio.file.Path;

WechatPayClient client = WechatPayClient.builder()
        .mchid("1900000001")
        .merchantPrivateKey(Path.of("/secure/apiclient_key.pem"))
        .merchantCertificate(Path.of("/secure/apiclient_cert.pem"))
        .apiV3Key(System.getenv("WECHAT_PAY_API_V3_KEY"))
        .wechatPayPublicKeyId("PUB_KEY_ID_0000000001")
        .wechatPayPublicKey(Path.of("/secure/wechatpay_public_key.pem"))
        .build();
```

商户证书用于自动提取 `serial_no` 并检查私钥是否配对。不便加载证书时，可改用 `.merchantSerialNo(...)`。私钥和公钥也可
直接传入 JCA `PrivateKey`、`PublicKey`，便于接入 HSM 或云密钥服务。

`WechatPayClient` 线程安全，应作为长生命周期对象复用并在应用停止时关闭。默认 HTTP 客户端由它拥有；通过
`.httpClient(...)` 传入的客户端视为借用，关闭微信支付客户端不会关闭借入对象。
借入客户端必须关闭自动重试、HTTP 重定向和跨协议重定向；构建器会检查并拒绝不安全配置，以保持支付调用的显式失败语义。
微信支付服务端会在连接空闲 8 秒后主动断开（`Keep-Alive: timeout=8`），默认 HTTP 客户端的空闲连接只保留
`WechatPayClient.DEFAULT_CONNECTION_KEEP_ALIVE`（5 秒）；借入客户端也应通过 `connectionPool(...)` 设置不超过该值的保留时间，
否则关闭重试后复用已断开的连接会直接得到 `kind=IO` 传输失败。
调用方配置的拦截器属于可信边界，不得改写或记录签名、正文、APIv3 密钥或敏感响应。

## Native 下单

一个商户号可以绑定多个 APPID。应用上下文固定绑定一个 `appid` 和支付通知地址，但共享根客户端、商户私钥与连接池：

```java
WechatPayApplication application = client.application(
        "wx1234567890",
        "https://pay.example.com/wechat/transaction-notify");

NativePrepayResponse response = application.nativePay().prepay(
        NativePrepayRequest.builder()
                .description("订单 ORDER_001")
                .outTradeNo("ORDER_001")
                .amount(100) // 单位：分
                .build());

URI codeUrl = response.codeUrl();
```

`codeUrl` 由前端或调用方选择二维码组件渲染。本模块不引入二维码图片依赖。
微信支付返回的 `code_url` 有效期为 2 小时；过期后应使用相同下单参数重新请求以获取新链接。

需要单品、门店或分账标识时，可继续配置对应业务模型：

```java
NativePrepayRequest request = NativePrepayRequest.builder()
        .description("深圳门店订单")
        .outTradeNo("ORDER_002")
        .amount(528800)
        .detail(NativePrepayDetail.builder()
                .addGoodsDetail(NativePrepayDetail.GoodsDetail.builder()
                        .merchantGoodsId("IPHONE_001")
                        .goodsName("iPhone")
                        .quantity(1)
                        .unitPrice(528800)
                        .build())
                .build())
        .sceneInfo(NativePrepaySceneInfo.builder()
                .payerClientIp("203.0.113.10")
                .deviceId("POS_001")
                .build())
        .profitSharing(false)
        .build();
```

## 查单、关单与退款

```java
var transaction = client.transactions().queryByOutTradeNo("ORDER_001");
var paid = client.transactions().queryByTransactionId("4200000000000000001");

// 入账前使用后端可信订单记录核对 APPID、订单号和金额。
// 微信只保证支付成功时返回金额：已关闭等未付款交易不含金额时只核对 APPID 和订单号，返回了金额则必须一致。
paid.requireOrder("wx1234567890", "ORDER_001", 100);

// 本地已经保存微信侧标识时，继续完整核对微信支付订单号和付款人 OpenID。
paid.requirePaidOrder(
        "wx1234567890",
        "ORDER_001",
        "4200000000000000001",
        "openid-from-trusted-record",
        100);

// 仅对仍处于 NOTPAY 的订单关单。
client.transactions().close("ORDER_001");

var refund = client.refunds().apply(RefundRequest.builder()
        .outTradeNo("ORDER_001")       // 与 transactionId 二选一
        .outRefundNo("REFUND_001")
        .amount(50, 100)                // 本次退款金额、原订单金额，单位均为分
        .reason("用户取消")
        .notifyUrl(URI.create("https://pay.example.com/wechat/refund-notify"))
        .build());

var latest = client.refunds().queryByOutRefundNo("REFUND_001");
latest.requireRefund(
        "ORDER_001",
        "4200000000000000001",
        "REFUND_001",
        "5000000000000000001",
        100,
        50);
```

退款申请成功只表示已受理。最终状态应结合退款通知和退款查询确认。网络超时后不要更换 `out_refund_no`，应使用相同参数显式
重试或查询原退款单。

## 回调通知

通知解析器不依赖 Web 框架。调用方需要把四个 `Wechatpay-*` 请求头转换为 `lava-http` 的 `HttpHeaders`，并传入未经修改的
原始请求正文：

```java
HttpHeaders headers = HttpHeaders.of(
        "Wechatpay-Serial", requestHeader("Wechatpay-Serial"),
        "Wechatpay-Signature", requestHeader("Wechatpay-Signature"),
        "Wechatpay-Timestamp", requestHeader("Wechatpay-Timestamp"),
        "Wechatpay-Nonce", requestHeader("Wechatpay-Nonce"),
        "Wechatpay-Signature-Type", requestHeader("Wechatpay-Signature-Type"));

TransactionNotification notification =
        client.notifications().parseTransaction(headers, rawRequestBody);
notification.requireOrder("wx1234567890", "ORDER_001", 100);

RefundNotification refundNotification =
        client.notifications().parseRefund(headers, rawRequestBody);
refundNotification.requireRefund("ORDER_001", "REFUND_001", 100, 50);
```

解析顺序固定为时间戳校验、RSA 验签、通知类型校验、AES-GCM 解密、商户号校验。成功后应用应在 5 秒内返回 HTTP 200 或
204，再异步处理业务；验签失败（包括微信支付的 `WECHATPAY/SIGNTEST/` 探测签名）必须返回 4xx 或 5xx，不能特殊放行。
通知可能重复发送，业务必须按通知 ID 或业务单号实现幂等。
验签通过只证明通知来自微信支付；更新本地订单前，仍必须将通知中的 `appid`、商户订单号、微信支付订单号、币种和金额与
后端可信订单记录逐项比对，不能直接信任前端参数或仅凭通知内容入账。首次成功通知通常尚无本地微信支付订单号，可先使用
`requireOrder(...)` 核对下单前已有字段，并在同一业务事务中保存微信支付订单号与 OpenID；重复通知或主动查单时使用
`requirePaidOrder(...)` 完成全量核对。退款模型同样提供包含微信支付订单号和退款单号的完整 `requireRefund(...)` 重载。

## 账单

```java
BillDownloadInfo info = client.bills().applyTradeBill(
        TradeBillRequest.builder()
                .billDate(LocalDate.of(2026, 8, 28))
                .billType(TradeBillType.ALL)
                .build());

BillDownloadResult result = client.bills().download(
        info, Path.of("/data/bills/2026-08-28.csv"));
```

下载过程先写同目录临时文件，SHA-1 与申请账单结果一致后才发布目标文件。目标已存在时拒绝覆盖；申请 `GZIP` 时会先解压，
再校验并保存账单原文，但不会解析账单字段。

发布使用同目录硬链接，目标名称即使在下载期间被占用也不会覆盖。目标文件系统必须支持硬链接；不支持时返回
`WechatPayFileException`（`IO`），并清理临时文件。

## 失败与重试

公开异常统一位于 `com.zhengshuyun.lava.pay.wechat.exception` 包：

- `WechatPayApiException`：微信支付返回的 HTTP 状态码、错误码、错误详情和 `Request-ID`；`verified()` 为 `false` 表示网关层未签名错误（如请求签名错误的 401 `SIGN_ERROR`），只能用于诊断，不能据此更新支付状态（例如判断 `ORDER_NOT_EXIST` 前先确认 `verified()`）；
- `WechatPayTransportException`：DNS、连接、TLS、超时等传输失败；
- `WechatPaySecurityException`：签名、公钥 ID、时间戳、回调密文、响应一致性或账单摘要校验失败；
- `WechatPayProtocolException`：响应不符合 APIv3 结构；
- `WechatPayFileException`：账单目标冲突或文件系统失败。

模块不会自动重试、轮询或切换主备域名。需要使用备域名时显式配置：

```java
.apiBaseUrl(WechatPayClient.BACKUP_API_BASE_URL)
```

生产 API 根地址只允许微信支付官方主、备域名，防止不包含主机名的签名请求被第三方中继；自定义地址仅允许
`localhost`、`127.0.0.1` 或 `::1` 环回主机进行本地协议测试。

签名、APIv3 密钥、下载 token、原始响应体和错误值不会进入默认异常文本。业务日志仍应避免直接输出请求模型、通知密文和
异常详情中的原始 `value`。

## 官方文档

- [Native 支付开发指引](https://pay.weixin.qq.com/doc/v3/merchant/4012791891)
- [APIv3 如何签名和验签](https://pay.weixin.qq.com/doc/v3/merchant/4012365342)
- [从平台证书切换成微信支付公钥](https://pay.weixin.qq.com/doc/v3/merchant/4012154180)
- [支付成功回调通知](https://pay.weixin.qq.com/doc/v3/merchant/4012791882)
- [退款申请](https://pay.weixin.qq.com/doc/v3/merchant/4013071036)
- [退款结果回调通知](https://pay.weixin.qq.com/doc/v3/merchant/4013071196)
- [下载账单](https://pay.weixin.qq.com/doc/v3/merchant/4013071238)
