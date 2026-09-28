# lava-pay-alipay

`lava-pay-alipay` 是面向支付宝 OpenAPI 普通商户公钥模式的同步 Java 工具包，不依赖支付宝官方 SDK。模块基于 Lava HTTP、
JSON、Crypto 与 JDK RSA 能力，实现 OpenAPI V3 REST 请求签名、响应验签、电脑网站与手机网站支付表单、小程序支付交易创建、交易查询与关闭、退款、
退款查询、银行卡冲退通知解析和账单下载地址查询。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-pay-alipay</artifactId>
</dependency>
```

版本建议由 [`lava-bom`](../lava-bom/README.md) 管理。

## 接入边界

当前版本支持：

- 自研普通商户、RSA2 公钥模式；
- `alipay.trade.page.pay` 电脑网站支付 AOP POST 表单和 GET 支付 URL；
- `alipay.trade.wap.pay` 手机网站支付 AOP POST 表单和 GET 支付 URL；
- OpenAPI V3 `alipay.trade.create` 小程序支付（JSAPI）交易创建；
- OpenAPI V3 `alipay.trade.query` 查单和 `alipay.trade.close` 关单；
- OpenAPI V3 `alipay.trade.refund` 退款和 `alipay.trade.fastpay.refund.query` 退款查询；
- 支付结果通知和 `alipay.trade.refund.depositback.completed` 银行卡冲退通知；
- OpenAPI V3 `alipay.data.dataservice.bill.downloadurl.query` 日/月账单下载地址查询。

支付宝当前采用混合协议，这不是工具包的兼容模式：

| 能力 | 官方实际协议 |
| --- | --- |
| 页面支付 | `POST /gateway.do` AOP 自动提交 HTML 表单，或 `GET /gateway.do` AOP 支付 URL |
| 小程序交易创建、查单、关单、退款、退款查询 | `POST /v3/...`，JSON + V3 Authorization |
| 账单下载地址 | `GET /v3/...`，query + V3 Authorization |
| 支付、退款冲退通知 | URL 编码表单 + RSA2 V1 参数验签 |

支付宝官方 `alipay-sdk-java-v3` 和当前页面支付文档均未提供 `/v3/alipay/trade/page/pay` 与 `/v3/alipay/trade/wap/pay`。工具包不会构造不存在的 REST
端点；除页面支付和通知这两个官方协议例外外，服务端 API 均使用真正的 OpenAPI V3。

当前不包含 App 支付、当面付、服务商代调用、公钥证书模式、直付通支付交易、分账、花呗分期、指定买家、开票、账单文件下载及解析。模块只负责协议适配，
不负责业务支付订单、业务幂等、渠道路由、通知持久化、轮询补偿和对账差异处理。

## 创建客户端

调用方需要准备应用 ID、卖家支付宝用户 ID、PKCS#8 应用私钥和支付宝公钥。Java 快速沙箱配置必须使用返回的
`appPrivateKey` 字段，不能改用 PKCS#1 `appPrivatePkcsKey`，也不能自行添加 PEM 头尾或转换格式。

```java
import com.zhengshuyun.lava.pay.alipay.AlipayClient;

import java.nio.file.Path;

AlipayClient client = AlipayClient.builder()
        .appId(System.getenv("ALIPAY_APP_ID"))
        .sellerId(System.getenv("ALIPAY_SELLER_ID"))
        .appPrivateKey(Path.of("/secure/alipay_app_private_key.pem"))
        .alipayPublicKey(Path.of("/secure/alipay_public_key.pem"))
        .build();
```

客户端线程安全，应作为长生命周期对象复用并在应用停止时关闭。默认 HTTP 客户端由它拥有；通过 `.httpClient(...)` 借入的
客户端不会随支付宝客户端关闭。借入客户端必须关闭自动重试、HTTP 重定向和跨协议重定向；构建器会检查并拒绝不安全配置，
让支付调用保持显式失败语义。调用方配置的拦截器属于可信边界，不得改写或记录签名、正文和敏感响应。

沙箱联调时显式切换 OpenAPI 基础地址：

```java
.baseUrl(AlipayClient.SANDBOX_BASE_URL)
```

生产配置只接受支付宝正式或沙箱 OpenAPI 域名，防止不包含主机名的 V3 签名请求被第三方中继；自定义地址仅允许环回主机进行
本地协议测试。

## 电脑网站支付

页面支付没有 REST V3 端点。它按支付宝官方 `pageExecute` 语义，在商户服务端生成完整的 AOP 签名请求；支付宝建议优先使用 POST 自动提交 HTML 表单：

```java
import com.zhengshuyun.lava.pay.alipay.order.PayForm;
import com.zhengshuyun.lava.pay.alipay.pagepay.PagePayRequest;

var pagePay = client.pagePay(
        "https://pay.example.com/alipay/notify",
        "https://pay.example.com/alipay/return");

PayForm form = pagePay.createForm(PagePayRequest.builder()
        .outTradeNo("ORDER_001")
        .totalAmount(100) // 单位：分
        .subject("订单 ORDER_001")
        .build());

httpResponse.setContentType(PayForm.CONTENT_TYPE);
httpResponse.getWriter().write(form.html());
```

必须把 `form.html()` 作为 HTML 页面渲染并自动提交，不能将这段 HTML 赋给 `window.location.href`。如需由前端直接打开或重定向，使用
`createUrl(...)` 获取 GET 支付 URL：

```java
import java.net.URI;

URI paymentUrl = pagePay.createUrl(PagePayRequest.builder()
        .outTradeNo("ORDER_001")
        .totalAmount(100) // 单位：分
        .subject("订单 ORDER_001")
        .build());

// 将 paymentUrl 交给前端直接打开或重定向；不得记录完整 URL。
```

GET URL 含完整业务参数和签名，不能写入日志、监控标签或分析平台。支付宝限制页面跳转数据最多 `16384` 个字符；超过此限制时
`createUrl(...)` 抛出协议异常，调用方应改用 `createForm(...)`。`product_code` 固定为 `FAST_INSTANT_TRADE_PAY`，
`integration_type` 固定为 `PCWEB`；应用 ID、`notify_url` 和 `return_url` 均由客户端参与签名并注入。

前台同步返回只用于页面展示，不代表支付成功。`return_url` 处理逻辑应主动调用查单接口确认结果。

## 手机网站支付

手机浏览器中的 H5 页面使用 `alipay.trade.wap.pay`：已安装支付宝时唤起客户端，否则进入支付宝 H5 收银台。用法与电脑网站支付一致，
同样返回 `PayForm` 或 GET URL：

```java
import com.zhengshuyun.lava.pay.alipay.wappay.WapPayRequest;

var wapPay = client.wapPay(
        "https://pay.example.com/alipay/notify",
        "https://pay.example.com/alipay/return");

PayForm form = wapPay.createForm(WapPayRequest.builder()
        .outTradeNo("ORDER_002")
        .totalAmount(100) // 单位：分
        .subject("订单 ORDER_002")
        .quitUrl("https://shop.example.com/order/ORDER_002") // 可选：用户中途退出时返回的页面
        .build());
```

`product_code` 固定为 `QUICK_WAP_WAY`。电脑网站支付的二维码选项在手机网站支付中不存在。

## 小程序支付

支付宝小程序使用 OpenAPI V3 `alipay.trade.create`（产品码 `JSAPI_PAY`）创建交易，前端再用返回的交易号唤起收银台：

```java
import com.zhengshuyun.lava.pay.alipay.jsapipay.JsapiPayRequest;
import com.zhengshuyun.lava.pay.alipay.jsapipay.JsapiPayResult;

JsapiPayResult result = client.jsapiPay("https://pay.example.com/alipay/notify")
        .create(JsapiPayRequest.builder()
                .outTradeNo("ORDER_003")
                .totalAmount(100) // 单位：分
                .subject("订单 ORDER_003")
                .buyerOpenId("小程序获取的买家 OpenID")
                .build());

// 小程序前端：my.tradePay({ tradeNO: result.tradeNo() })
```

`buyerOpenId` 与 `buyerId`（2088 开头的用户 ID，仅限按 UID 配置的存量应用）必须且只能配置一个。小程序与当前 OpenAPI
应用不是同一个时，通过 `opAppId(...)` 传入小程序 APPID。响应中的订单号会与请求核对，不一致时抛出 `AlipaySecurityException`。
交易创建成功不代表已付款，`my.tradePay` 的回调也不能作为入账依据，支付结果以异步通知或查单为准。

## 支付通知

Web 层把完成一次表单 URL 解码后的全部参数整理为 `Map<String, String>`，再交给框架无关解析器：

```java
var notification = client.notifications().parseTrade(formParameters);

// 可信值必须来自商户后端订单记录，不能来自前端或当前通知。
notification.requireOrder(trustedOutTradeNo, trustedAmountInCents);
if (notification.paid()) {
    // 按 notifyId 或业务单号幂等更新本地订单。
}

// 只有验签、业务匹配和可靠持久化全部成功后才返回 success。
return NotificationParser.SUCCESS;
```

解析器固定执行 RSA2 验签，并核对 `app_id`、`seller_id` 和通知类型；`requireOrder(...)` 再核对商户订单号与金额。只有
`TRADE_SUCCESS` 或 `TRADE_FINISHED` 表示买家付款成功。处理失败应返回 `fail`，让支付宝按策略重试。

## 查单与关单

```java
var trade = client.transactions().queryByOutTradeNo("ORDER_001");
trade.requireOrder("ORDER_001", 100); // 使用后端可信订单金额核对
if (trade.paid()) {
    // 已支付
}

// 仅关闭仍处于 WAIT_BUYER_PAY 的交易。
client.transactions().closeByOutTradeNo("ORDER_001");
```

未收到异步通知、支付请求结果未知或准备更换商户订单号重新支付前，必须先查单。前一订单仍为
`WAIT_BUYER_PAY` 时应先关单；已经支付时不得要求用户重复付款。

## 退款与退款查询

```java
import com.zhengshuyun.lava.pay.alipay.refund.RefundQueryRequest;
import com.zhengshuyun.lava.pay.alipay.refund.RefundRequest;

var refund = client.refunds().apply(RefundRequest.builder()
        .outTradeNo("ORDER_001")
        .outRequestNo("REFUND_001")
        .refundAmount(50) // 单位：分
        .reason("用户取消")
        .build());

if (!refund.succeeded()) {
    // V3 HTTP 成功只表示请求已处理；fund_change 不是 Y 时继续查询。
    var latest = client.refunds().query(RefundQueryRequest.builder()
            .outTradeNo("ORDER_001")
            .outRequestNo("REFUND_001")
            .build());
    latest.requireRefund("ORDER_001", "REFUND_001", 100, 50);
    boolean refunded = latest.succeeded(); // 仅 REFUND_SUCCESS 为 true
}
```

每笔退款必须使用稳定且唯一的 `outRequestNo`。网络超时或系统错误后，使用相同退款请求号和金额重试或查询，不能更换请求号。
退款默认请求 `deposit_back_info`，以便银行卡原路退款场景接收冲退完成通知：

```java
var depositBack = client.notifications()
        .parseRefundDepositBack(formParameters);
depositBack.requireRefund("ORDER_001", "REFUND_001", 50);

// 按 notifyId 和 outRequestNo 幂等处理，成功后返回 success。
```

## 账单

```java
import com.zhengshuyun.lava.pay.alipay.bill.BillType;

var info = client.bills().queryDaily(
        BillType.TRADE, java.time.LocalDate.now().minusDays(1));
var downloadUrl = info.downloadUrl();
```

完整请求可通过 `BillRequest.builder()` 配置直付通二级商户 SMID。模块只返回已验签下载信息，下载地址在取得后 30 秒内未使用
即会失效，调用方应立即下载，并自行完成文件存储、解压、字段解析和对账差异处理。

当前实现严格跟随官方 Java V3 SDK/OAS 中的 `bill_type`、`bill_date` 和 `smid` 参数，不发送 SDK 尚未收录的扩展字段。

## 失败语义与安全

- `AlipayApiException`：结构化支付宝 V3 API 失败，包含 HTTP 状态、`verified`、`code`、`apiMessage`、`details`、`links`
  和可选 `traceId`；官方 V3 SDK允许错误响应不带签名，此时 `verified=false`，只能用于诊断和重试分类；
- `AlipayTransportException`：DNS、连接、TLS、超时或非成功 HTTP 状态，结果可能未知；
- `AlipaySecurityException`：签名、应用、卖家、订单号或金额不匹配；
- `AlipayProtocolException`：响应结构、金额精度或日期格式不符合协议。

模块不会自动重试、轮询或将未知结果判为失败。私钥、签名原文、完整响应正文和账单下载令牌不会进入默认异常文本；业务日志仍不得
记录私钥、完整通知参数或支付表单。

## 官方文档

- [OpenAPI V3 请求规则](https://opendocs.alipay.com/open-v3/054oog)
- [OpenAPI V3 请求签名](https://opendocs.alipay.com/open-v3/054q58)
- [OpenAPI V3 响应验签](https://opendocs.alipay.com/open-v3/054d0z)
- [统一收单下单并支付页面接口](https://opendocs.alipay.com/open-v3/2423fad5_alipay.trade.page.pay?scene=22&pathHash=1dc76737)
- [统一收单交易关闭](https://opendocs.alipay.com/open-v3/429ffb46_alipay.trade.close?scene=common&pathHash=4d948bd6)
- [统一收单交易退款](https://opendocs.alipay.com/open-v3/01073208_alipay.trade.refund?scene=common&pathHash=dff16ab4)
- [退款冲退完成通知](https://opendocs.alipay.com/open-v3/42a9ce75_alipay.trade.refund.depositback.completed?scene=common&pathHash=9c33d734)
- [统一收单交易查询](https://opendocs.alipay.com/open-v3/e9ce4f59_alipay.trade.query?scene=23&pathHash=9b1a06e3)
- [统一收单交易退款查询](https://opendocs.alipay.com/open-v3/46bff59c_alipay.trade.fastpay.refund.query?scene=common&pathHash=3901bb82)
- [查询对账单下载地址](https://opendocs.alipay.com/open-v3/d6c4d425_alipay.data.dataservice.bill.downloadurl.query?scene=common&pathHash=b88e9ae1)
