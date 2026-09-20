# lava-jiandaoyun

`lava-jiandaoyun` 是简道云开放平台的 API 工具包，不依赖简道云官方 SDK。模块基于 lava-http 与 lava-json 直接对接开放接口，负责 Bearer 鉴权、结构化错误映射和 JSON 模型编解码，不负责业务侧数据同步状态机。

## 支持范围

| 能力 | 说明 |
| --- | --- |
| 应用查询 | 获取 API Key 授权范围内的应用列表（`app/list`） |
| 表单查询 | 获取指定应用下的表单列表（`app/entry/list`） |
| 表单字段 | 获取表单字段结构、系统字段和数据最新修改时间（`app/entry/widget/list`） |

## 接入模式

当前实现固定为：

- v5 版本开放接口；
- 单一 API Key 的 Bearer 鉴权；
- 只读查询能力；数据读写、通讯录和文件等域按需扩展。

## 失败语义

| 异常 | 含义 |
| --- | --- |
| `JiandaoyunApiException` | 简道云返回非 2xx，携带 HTTP 状态码、错误码（如 8303 超频）和描述 |
| `JiandaoyunTransportException` | DNS、连接、TLS、超时等 HTTP 传输失败，已脱敏 |
| `JiandaoyunProtocolException` | 响应无法按简道云协议解释（如错误正文缺少 code/msg） |

非法调用参数一律抛出 `IllegalArgumentException`，不进入领域异常体系。

频率限制：全局 50 次/秒，以上三个接口各 30 次/秒。超频错误码 8303/8304 可等待 1 秒后重试，本模块不做自动重试，调用方可配合 lava-core 的 `RetryPolicy` 自行处理。

## 下一步

- [快速开始](./quick-start.md)
