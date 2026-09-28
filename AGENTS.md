# 项目开发规范

## Builder 命名

- 项目自有 Builder 统一使用 fluent 风格，配置属性的方法直接使用属性名，例如 `baseUrl(...)`、`connectTimeout(...)`、
  `bearerToken(...)`，不得使用 `setXxx(...)`。
- 集合或动作型方法保留明确语义，例如 `addHeader(...)`、`remove(...)`、`customize(...)`；表达替换语义的 `set(...)` 可以保留。
- 不为旧的 `setXxx(...)` 方法保留兼容别名，测试、文档和示例必须使用统一后的 fluent API。
- 第三方库和 JDK API 不受该命名规则约束。

## 工具类命名

- 项目自有工具类统一以 `Utils` 结尾，例如 `ValidationUtils`、`IdUtils`、`HttpBodyUtils`。
- 工具类指不可实例化、主要通过静态方法提供通用辅助能力的类。
- 创建领域对象的静态辅助类同样遵循该规则，不再使用复数领域名作为工具类名。
- 领域模型、Builder、协议适配器和常量容器不应仅为满足后缀规则而命名为 `Utils`。

## 微信支付接入

- `lava-pay-wechat` 当前按境内 APIv3 普通商户模式实现，验签使用微信支付公钥，不使用平台证书模式。
- 支付产品共用商户级签名、验签、通知解密、交易、退款和账单能力；新增 JSAPI、小程序或 APP 支付时复用现有根客户端与应用上下文，不复制协议实现。

## 简道云接入

- `lava-jiandaoyun` 按「根客户端 + 领域子客户端」组织：Bearer 鉴权、错误映射和 JSON 编解码集中在 internal 传输层；新增领域必须复用根客户端与共享传输层（传输层同时持有关闭状态与 HTTP 客户端所有权），不复制协议实现。
- 领域子客户端按开放文档分类分包（如 `application`、`form`），类名 = 包名驼峰 + Client，列表类接口的分页参数用请求对象 + Builder 表达。
- 简道云 v6 起开放 API 只增不减出入参，所有响应模型必须 `@JsonIgnoreProperties(ignoreUnknown = true)`。
- 上游命名陷阱：表单字段的 `name` 是别名（无别名时为字段标识），`widgetName` 才是控件 ID，不要按字面含义误用。
