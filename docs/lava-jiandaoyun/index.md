# lava-jiandaoyun

`lava-jiandaoyun` 是简道云开放平台的 API 工具包，不依赖简道云官方 SDK。模块基于 lava-http 与 lava-json 直接对接开放接口，负责 Bearer 鉴权、结构化错误映射、JSON 模型编解码和数据写入的 `value` 包装，不负责业务侧数据同步状态机。

## 支持范围

覆盖简道云开放平台 API 文档列出的全部接口：

| 入口 | 能力 | 接口 |
| --- | --- | --- |
| `applications()` | 应用列表 | `app/list` |
| `forms()` | 表单列表、表单字段 | `app/entry/list`、`app/entry/widget/list` |
| `data()` | 单条/多条数据的查询、新建、修改、删除，按游标自动翻页 | `app/entry/data/*` |
| `files()` | 获取上传凭证、上传附件和图片 | `app/entry/file/get_upload_token` + 对象存储 |
| `workflows()` | 流程实例查询/结束/激活、流程日志、审批意见；待办查询、提交、否决、回退、转交、加签、撤回；抄送列表 | `workflow/*`、`approval_comments` |
| `members()` | 成员查询、添加、修改、删除、批量删除、增量导入 | `corp/user/*` |
| `departments()` | 部门列表、部门成员、增删改、集成模式部门编号、全量导入、部门主管 | `corp/department/*` |
| `roles()` / `roleGroups()` | 角色与角色组的增删改查、角色成员增减 | `corp/role/*`、`corp/role_group/*` |
| `guests()` | 企业互联的连接企业与对接人 | `corp/guest/*` |
| `usage()` | 平台/应用/成员资源用量 | `corp_usage/*` |
| `auditLogs()` | 审计日志范围与明细 | `audit_log/*` |
| `JiandaoyunWebhookUtils` | 推送验签（`X-JDY-Signature`）与事件解析 | — |

## 设计约定

- **写入值自动包装**：新建/修改数据时直接传 `Map.of("_widget_1", "张三")`，SDK 转为简道云要求的 `{"_widget_1": {"value": "张三"}}`；子表单写成行列表，行内字段（含行 `_id`）同样自动包装；值为 `null` 表示清空字段。
- **动态字段**：数据记录 `DataRecord` 用 `value(字段名)` 读取业务字段，`id()`、`creator()`、`createTime()` 等访问系统字段，也可以 `as(Class)` 转为自定义 POJO。
- **向前兼容**：简道云 v6 起出入参只增不减，所有响应模型忽略未知字段；状态、类型等上游枚举值以 `int` 暴露，避免上游新增取值导致解析失败。
- **只做必要校验**：客户端只校验必填项和无法发出请求的情况，条数上限、字段长度等业务规则由简道云服务端校验。
- **上传不带密钥**：文件上传发往对象存储地址，请求不携带简道云 API Key，且上传地址必须是 HTTPS。

## 失败语义

| 异常 | 含义 |
| --- | --- |
| `JiandaoyunApiException` | 简道云返回业务失败：非 2xx 响应，或流程类接口 2xx 下返回 `{"status": "failure"}`；携带 HTTP 状态码、错误码（如 8303 超频、4008 流程已关闭）和描述 |
| `JiandaoyunTransportException` | DNS、连接、TLS、超时等 HTTP 传输失败，已脱敏 |
| `JiandaoyunProtocolException` | 响应无法按简道云协议解释（如错误正文缺少 code/msg、推送正文缺少 op） |

非法调用参数一律抛出 `IllegalArgumentException`，不进入领域异常体系。

频率限制：全局 50 次/秒，各接口另有单独限制（数据查询 30 次/秒、批量写入 10 次/秒、资源用量 1 次/秒等）。超频错误码 8303/8304 可等待 1 秒后重试，本模块不做自动重试，调用方可配合 lava-core 的 `RetryPolicy` 自行处理。

## 下一步

- [快速开始](./quick-start.md)
