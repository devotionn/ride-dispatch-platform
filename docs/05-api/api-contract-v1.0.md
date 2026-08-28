# API 契约草案 V1.0

- 状态：DRAFT
- 目的：在正式编码前统一 H5、后台、Android 和后端边界。
- 注意：以下为接口结构草案，最终字段以实现时的 OpenAPI 契约和已确认 PRD 为准。

## 1. 基础规范

- Base path：`/api/v1`
- JSON：UTF-8
- 时间：ISO-8601
- 金额：API 推荐传整数最小货币单位或明确 Money 对象，禁止模糊浮点金额。
- 请求追踪：返回/接收 `X-Request-Id`。
- 写接口的业务失败使用稳定业务错误码，不让客户端解析中文字符串。

## 2. 认证模型

### 2.1 乘客 H5

创建订单无需账号登录。

订单创建成功后返回不可预测访问令牌；后续查询/取消携带该令牌。

### 2.2 司机 App

登录后使用短期 Access Token + 可刷新机制；司机权限只能访问自己相关订单和账户。

### 2.3 管理后台

登录后按 RBAC 鉴权：ADMIN / DISPATCHER / FINANCE。

## 3. 统一响应建议

成功：

```json
{
  "data": {},
  "requestId": "..."
}
```

业务失败：

```json
{
  "code": "ORDER_STATE_CONFLICT",
  "message": "订单状态已变化，请刷新后重试",
  "requestId": "..."
}
```

## 4. 幂等

以下接口必须支持幂等或等价的防重机制：

- 创建乘客订单；
- 司机接受/拒绝；
- 履约阶段推进；
- 创建支付尝试；
- 支付回调；
- 提现申请；
- 人工打款确认。

HTTP 可使用 `Idempotency-Key`，服务端保存 key 与结果映射并限制作用域。

## 5. 乘客 API

### 5.1 获取平台品牌

`GET /public/brand`

### 5.2 解析司机短码

`GET /public/drivers/by-short-code/{shortCode}`

仅返回乘客下单页需要的安全信息，不泄露司机隐私数据。

### 5.3 创建订单

`POST /public/orders`

请求示意：

```json
{
  "sourceType": "PUBLIC_H5",
  "driverShortCode": null,
  "pickup": {
    "address": "扬州东站",
    "latitude": 32.391,
    "longitude": 119.508
  },
  "destination": {
    "address": "瘦西湖",
    "latitude": 32.420,
    "longitude": 119.414
  },
  "passengerCount": 2,
  "departureAt": "2026-08-21T15:30:00+08:00",
  "mobile": "...",
  "remark": "..."
}
```

返回至少包括：orderNo、status、passengerAccessToken。

### 5.4 查询订单

`GET /public/orders/{orderNo}?accessToken=...`

生产实现更推荐令牌放授权头/安全 Cookie，避免在日志中泄漏 Query Token。

### 5.5 乘客取消

`POST /public/orders/{orderNo}/cancel`

服务端再次判断是否仍处于接单前允许取消的状态。

### 5.6 付款上下文

`GET /public/payments/by-token/{payToken}`

仅返回当前订单应付信息和可用支付方式。

## 6. 司机 API

### 6.1 登录

`POST /auth/driver/login`

### 6.2 工作状态

`GET /driver/me/state`

返回当前司机工作状态、当前可接人数和车辆最大载客人数。

`PUT /driver/me/work-status`

### 6.3 当前可接人数

`PUT /driver/me/available-passengers`

服务端校验不超过车辆最大人数。

### 6.4 上报位置

`POST /driver/me/location`

建议携带：lat/lng、accuracy、locatedAt、source。

### 6.5 我的二维码

`GET /driver/me/qr`

返回短码、司机定向下单 H5 URL，以及可直接展示的 PNG `imageDataUrl`。

### 6.6 待确认订单

`GET /driver/orders/pending-confirmation`

### 6.7 接受

`POST /driver/dispatch-attempts/{attemptId}/accept`

关键校验：

- Attempt 仍 WAITING；
- targetDriver 是当前司机；
- Order 未被取消/改派；
- version/锁检查通过。

### 6.8 拒绝

`POST /driver/dispatch-attempts/{attemptId}/reject`

必须提交拒绝原因代码或其他文本。

### 6.9 履约推进

`POST /driver/orders/{orderNo}/progress`

```json
{
  "stage": "ARRIVED_PICKUP"
}
```

### 6.10 确认最终金额

`POST /driver/orders/{orderNo}/final-amount`

请求体中的 `amount` 为整数“分”；司机端等业务界面以“元”录入，提交前换算为分，避免浮点金额进入服务端。

### 6.11 线下收款确认

`POST /driver/orders/{orderNo}/offline-payment/confirm`

要求二次确认由客户端交互 + 服务端明确 action 共同完成。

### 6.12 收入/账户/提现

本地环境的金额边界保持一致：页面和司机输入使用“元”，接口和账本使用整数“分”。线上 Mock 支付成功增加可提现余额，线下收款只记录业务收入；所有余额变更都返回服务端最新快照。

- `GET /driver/me/account`
- `GET /driver/me/ledger`
- `GET /driver/me/withdrawals`
- `POST /driver/me/withdrawals`

### 6.13 实时事件

- `GET /driver/events`：司机 Bearer Token 订阅 SSE 事件。
- 事件只用于快速刷新和新单提示，客户端断线后必须回退查询订单/状态接口，服务端状态机仍是最终真相。

## 7. 管理后台 API

### 7.1 品牌
- `GET /admin/brand`：管理员 / 调度员可读。
- `PUT /admin/brand`：仅管理员；当前实现返回 `id`、`companyName`、`logoUrl`、`updatedAt`。
- `POST /admin/brand/logo`：仅管理员；multipart 上传 PNG/JPEG/WebP，最大 2MB，成功后返回最新品牌信息和受控公开 URL。

### 7.2 司机

- `GET /admin/drivers`
- `POST /admin/drivers`
- `GET /admin/drivers/{id}`
- `PUT /admin/drivers/{id}`
- `PUT /admin/drivers/{id}/status`
- `GET /admin/drivers/{id}/qr`

### 7.3 订单

- `GET /admin/orders`
- `GET /admin/orders/{orderNo}`
- `POST /admin/orders`（代客建单）
- `POST /admin/orders/{orderNo}/cancel`
- `POST /admin/orders/{orderNo}/mark-exception`

### 7.4 附近司机

`GET /admin/orders/{orderNo}/nearby-drivers`

返回：司机、安全展示信息、直线距离、定位时间、可接人数、工作状态。

### 7.5 派单

`POST /admin/orders/{orderNo}/dispatch`

```json
{
  "driverId": 123
}
```

### 7.6 改派/强制改派

- 待司机确认：`POST /admin/orders/{orderNo}/reassign`。
- 已接单/执行中强制改派：`POST /admin/orders/{orderNo}/force-reassign`，必须携带 `driverId` 和不可空白 `reason`。

当前实现语义：发起强制改派后，订单进入待新司机确认；确认前，原 `currentDriverId` 保持为责任司机。新司机拒绝时回滚到原司机负责；接受时完成责任交接。该语义是保守实现，PRD V1.4 CHANGE-002 仍需甲方正式确认。

### 7.6.1 强制取消

已接单/服务中订单使用：

`POST /admin/orders/{orderNo}/force-cancel`

必须携带不可空白 `reason`。系统取消订单、失效等待中的派单尝试并写入 OperationLog。强制改派待新司机确认期间，乘客不能自行取消。
### 7.7 支付与线下修正

- `GET /admin/payments`
- `GET /admin/payments/{paymentNo}`
- `POST /admin/payments/{paymentNo}/offline-adjustments`

### 7.8 提现

- `GET /admin/withdrawals`
- `POST /admin/withdrawals/{id}/approve`
- `POST /admin/withdrawals/{id}/reject`
- `POST /admin/withdrawals/{id}/mark-paid`

### 7.9 人工退款异常

本地版本由管理员或财务登记需要人工核验的退款申请；该流程只记录处理凭证和审计日志，不调用真实支付渠道，也不自动改动支付状态或司机账本。

- `GET /admin/payment-exceptions`
- `POST /admin/payment-exceptions`（必须携带 `Idempotency-Key`；同一支付单有效异常累计金额不得超过原支付金额）
- `POST /admin/payment-exceptions/{exceptionNo}/resolve`
- `POST /admin/payment-exceptions/{exceptionNo}/reject`

### 7.10 审计

`GET /admin/operation-logs`

## 8. 支付回调 API

示意：

- `POST /callbacks/payments/wechat`
- `POST /callbacks/payments/alipay`

要求：

- 独立于普通用户鉴权；
- 按 Provider 签名验证；
- 原始请求用于验签但业务日志脱敏；
- 幂等；
- 成功处理后按支付平台协议返回正确 ACK。

### 8.1 Provider 边界（前置模型）

真实 Provider 适配器必须实现下单、回调验签和主动查询三个边界；适配器只返回不可变结果，不直接修改 `Payment`、`Order` 或 `DriverLedger`。业务服务在验签、商户订单号、第三方流水号、整数分金额和幂等键校验通过后，才进入现有支付结算事务。

契约字段约束：

- `paymentNo`、`merchantOrderNo`、`idempotencyKey`、商品描述不可为空；
- 金额必须是正整数分，禁止浮点数和元字符串进入 Provider；
- 已支付回调/查询必须携带第三方流水号和金额；
- 回调必须保留原文和签名供 Provider 验证，业务日志不得记录完整敏感载荷。

当前注册表允许为空：生产未配置真实商户 Provider 时，调用方得到 `PAYMENT_PROVIDER_UNAVAILABLE`；`local` profile 的 Mock 回调仍由独立本地控制器提供，生产不会暴露该路由。

### 8.2 对账差异模型

对账比较器只读比较本地支付尝试快照与 Provider 查询快照，返回以下状态，不自动改账：

- `MATCHED`：状态、整数分金额和已支付流水一致；
- `LOCAL_PENDING_PROVIDER_PAID`：Provider 已支付但本地仍待支付，进入补偿队列；
- `AMOUNT_MISMATCH`：金额不一致，优先级高于其他差异；
- `TRANSACTION_MISMATCH`：两侧已支付但第三方流水不一致；
- `STATUS_MISMATCH`：状态不一致；
- `PROVIDER_ONLY` / `LOCAL_ONLY`：仅一侧存在记录。

该模型当前只负责分类和安全原因码，真实主动查询、补偿、退款和财务人工处理待商户参数齐全后接入。

## 9. 实时消息

实时通道只作为快速刷新，不作为最终业务真相。

可推送事件：

- DRIVER_NEW_DISPATCH
- DRIVER_DISPATCH_INVALIDATED
- ORDER_STATUS_CHANGED
- ADMIN_ORDER_UPDATED

客户端收到事件后应按需要重新拉取服务端详情。

## 10. 典型业务错误码

- ORDER_NOT_FOUND
- ORDER_ACCESS_DENIED
- ORDER_STATE_CONFLICT
- ORDER_ALREADY_CANCELLED
- DISPATCH_ATTEMPT_EXPIRED
- DISPATCH_ATTEMPT_NOT_TARGET_DRIVER
- DRIVER_LOCATION_STALE
- DRIVER_CAPACITY_INSUFFICIENT
- PAYMENT_ALREADY_SETTLED
- PAYMENT_AMOUNT_MISMATCH
- WITHDRAWAL_BALANCE_INSUFFICIENT
- WITHDRAWAL_STATE_CONFLICT
- IDEMPOTENCY_CONFLICT

## 11. OpenAPI

正式编码后以后端生成/维护的 OpenAPI 文件作为字段级契约，并在 CI 校验前后端依赖的破坏性变化。
