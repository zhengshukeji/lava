# lava-pay-alipay

`lava-pay-alipay` 是面向支付宝开放平台普通商户公钥模式的同步 Java 工具包，不依赖支付宝官方 SDK。模块负责协议签名、响应验签、页面支付参数生成和结果解析，不负责商户侧订单状态机。

## 支持范围

| 能力 | 支付宝接口 | 协议 |
| --- | --- | --- |
| 电脑网站支付 | `alipay.trade.page.pay` | AOP `gateway.do`，POST 表单或 GET URL |
| 手机网站支付 | `alipay.trade.wap.pay` | AOP `gateway.do`，POST 表单或 GET URL |
| 小程序支付 | `alipay.trade.create` | OpenAPI V3 REST |
| 交易查询 | `alipay.trade.query` | OpenAPI V3 REST |
| 交易关闭 | `alipay.trade.close` | OpenAPI V3 REST |
| 退款 | `alipay.trade.refund` | OpenAPI V3 REST |
| 退款查询 | `alipay.trade.fastpay.refund.query` | OpenAPI V3 REST |
| 账单下载地址 | `alipay.data.dataservice.bill.downloadurl.query` | OpenAPI V3 REST |
| 支付通知 | `trade_status_sync` | URL 编码表单 + RSA2 V1 参数验签 |
| 银行卡冲退通知 | `alipay.trade.refund.depositback.completed` | URL 编码表单 + RSA2 V1 参数验签 |

支付宝目前没有 `/v3/alipay/trade/page/pay` 与 `/v3/alipay/trade/wap/pay`。页面支付继续使用官方 AOP 页面跳转协议；小程序交易创建、查单、关单、退款和账单使用真正的 REST V3。这是支付宝接口本身的协议边界，不是模块提供的兼容模式。

## 不包含的能力

当前不包含：

- 服务商代调用和公钥证书模式；
- 直付通支付交易、分账、花呗分期和指定买家；
- App 支付和当面付；
- 业务订单、幂等持久化、渠道路由、轮询补偿；
- 账单文件下载、解压、解析与差异处理。

## 客户端入口

`AlipayClient` 是线程安全的根客户端，应在应用内长期复用：

| 方法 | 业务入口 |
| --- | --- |
| `pagePay(...)` | 电脑网站支付 |
| `wapPay(...)` | 手机网站支付 |
| `jsapiPay(...)` | 小程序支付 |
| `transactions()` | 查单和关单 |
| `refunds()` | 退款和退款查询 |
| `bills()` | 账单下载地址查询 |
| `notifications()` | 支付和银行卡冲退通知解析 |

从 [快速开始](./quick-start) 创建客户端，或直接查看 [电脑网站支付](./page-pay)。

::: danger 支付安全底线
私钥只能保存在商户服务端，不能进入客户端、日志或公共代码仓库。前台同步跳转不代表支付成功，必须以已验签异步通知或主动查单结果为准。
:::
