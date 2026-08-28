# 核心领域与业务规则 V1.0

- 状态：DRAFT
- 依据：PRD V1.3 + 已识别的工程一致性要求
- 注意：标记为 `PROPOSAL` 的内容不是 V1.3 已确认需求。

## 1. 核心聚合/实体

- PlatformBrand
- AdminUser / Role
- Driver
- Vehicle
- Order
- OrderProgressEvent
- DispatchAttempt
- Payment
- PaymentAttempt
- PaymentAdjustment
- DriverAccount
- DriverLedger
- Withdrawal
- OperationLog

## 2. Order 的业务事实

一个订单至少保存：

- 不可变业务订单号；
- 来源 `PUBLIC_H5 / DRIVER_QR / ADMIN_CREATED`；
- `source_driver_id`：仅司机二维码来源有值，后续改派不得覆盖；
- `current_driver_id`：当前真正负责履约的司机；
- 乘客手机号；
- A/B 点文字地址和经纬度；
- 乘车人数；
- 出发时间；
- 最终金额；
- 主状态；
- 支付方式/收款结果；
- 创建、完成、取消时间；
- 乐观锁版本号。

## 3. 主状态

建议后端枚举：

| 枚举 | 中文 | 允许的主要后续 |
| --- | --- | --- |
| PENDING_DISPATCH | 待接单 | 派单、取消、异常 |
| PENDING_DRIVER_CONFIRM | 待司机确认 | 接受、拒绝、改派、取消 |
| ACCEPTED | 已接单 | 开始履约、后台强制取消、强制改派 |
| IN_SERVICE | 执行中 | 推进履约、待付款、强制改派/取消、异常 |
| PENDING_PAYMENT | 待付款 | 线上支付成功、线下确认、异常 |
| COMPLETED | 已完成 | 仅允许受控财务/异常后续，不重新履约 |
| CANCELLED | 已取消 | 终态 |
| EXCEPTION | 异常 | 由后台人工处理，处理动作留痕 |

司机拒绝不是订单终态。

## 4. 履约进度

PRD 在“执行中”内部要求按顺序推进：

```text
ARRIVED_PICKUP
→ PASSENGER_ONBOARD
→ IN_TRANSIT
→ ARRIVED_DESTINATION
```

实现时建议把每次推进追加到 `OrderProgressEvent`，而不是只保留一个最后更新时间。

禁止无规则跳跃，例如未到上车点直接“到达目的地”。如需人工纠正，必须由后台走异常/审计流程。

## 5. DispatchAttempt

每次派单创建一条尝试：

```text
WAITING
├─ ACCEPTED
├─ REJECTED
├─ CANCELLED_BY_REASSIGN
└─ CANCELLED_BY_ORDER
```

字段至少包含：

- order_id
- target_driver_id
- dispatched_by
- dispatch_type
- dispatched_at
- responded_at
- status
- reject_reason_code / reject_reason_text
- reassign_from_driver_id
- reassign_reason

### 5.1 派单原则

- 一个订单同一时间最多一个有效等待确认的 DispatchAttempt。
- 旧 Attempt 一旦因改派失效，司机随后点击“接受”必须返回“该派单已失效”。
- 司机只能处理派给自己的 Attempt。
- 后台派单后显示等待时长，但 V1.3 不自动改派。

## 6. 执行中强制改派

V1.3 已确认：允许强制改派，必须二次确认、原因和日志。

### PROPOSAL：交接语义

现实执行中，建议不要在新司机接受前立即清空原 `current_driver_id`。

建议流程：

```text
原司机仍负责
→ 创建面向新司机的强制改派 Attempt
→ 新司机接受
→ 后台/系统完成交接确认
→ current_driver_id 切换
```

原因：乘客可能已经在原司机车上。该规则需业务确认后写入 PRD V1.4。

## 7. Driver 状态

不要用一个枚举同时表示所有司机状态。

建议拆为：

- account_status：ACTIVE / DISABLED
- work_status：AVAILABLE / PAUSED / OFFLINE
- max_passengers
- available_passengers（V1.3 规定司机手工维护）
- current_location + located_at

附近司机必须同时满足账号、工作状态、位置时效和人数条件。

## 8. 司机定位

- 仅在可接单状态按策略上报。
- `located_at` 超过 5 分钟即不进入附近候选。
- 5 分钟是失效阈值，不等于上传周期。
- H5 和 Android 使用的业务经纬度必须统一坐标体系。
- UI 明确显示“直线距离”，不伪装成驾车 ETA。

## 9. 三种入口

### PUBLIC_H5

- 不绑定司机。
- 创建后 PENDING_DISPATCH。

### DRIVER_QR

- 通过不可直接枚举的 driverShortCode 识别来源司机。
- 创建时记录 source_driver_id。
- 创建后 PENDING_DRIVER_CONFIRM。
- 目标司机拒绝后转 PENDING_DISPATCH，但 source_driver_id 保留。

### ADMIN_CREATED

- 后台代客创建。
- 创建后 PENDING_DISPATCH。

## 10. 乘客订单访问

### 实施规则

因为 V1.3 不做乘客账号/短信验证，建议每个订单生成随机 `passenger_access_token`。

Token 用于：

- 查看订单；
- 接单前取消；
- 查看当前状态；
- 进入相关付款上下文。

不得使用可枚举数据库 ID 作为唯一访问凭证。

## 11. 订单创建幂等

公共 H5 和司机二维码 H5 需要防止弱网/重复点击产生重复订单。

建议：

- 客户端生成 requestId；或
- 使用 HTTP `Idempotency-Key`。

服务端对“同一 key + 同一业务接口”只创建一次订单，并返回同一结果。

## 12. 价格

V1.3 不自动计价。

最终金额：

- 由司机与乘客线下协商；
- 到达目的地后司机录入；
- 进入付款阶段后应形成明确的应收款记录；
- 支付成功后不得直接覆写原成功支付金额。

金额使用整数最小货币单位。

## 13. Payment 与 PaymentAttempt

### Payment

表示订单当前应收：

- order_id
- amount
- status
- settled_method
- created_at / settled_at

### PaymentAttempt

表示第三方一次支付尝试：

- payment_id
- channel
- merchant_order_no
- third_party_transaction_no
- amount
- status
- created_at
- paid_at
- callback_payload_digest（可选）

约束：

- 一个 Payment 可多次 Attempt；
- 一个订单最终只能成功结算一次；
- 第三方流水号唯一；
- 回调必须幂等；
- 金额、商户、订单上下文必须校验。

## 14. 线下收款

V1.3 已确认司机可选择线下收款并二次确认。

### 关键实施规则

线下款如果已经直接由司机取得：

- 计入该司机“业务收入”；
- 默认不增加“平台可提现余额”。

否则会发生司机已经拿到现金/个人收款后又从平台提现同一笔收入的风险。

该规则应正式进入 PRD V1.4 确认。

### 14.1 Local Mock 结算策略

- 平台抽佣为 0。
- 线上 Mock 支付成功：`businessIncomeAmount = amount`，`withdrawableDelta = amount`。
- 线下收款确认：`businessIncomeAmount = amount`，`withdrawableDelta = 0`。
- 同一订单只允许一个成功 Payment；重复回调只能返回幂等结果，不能重复写入收入账本。
- 真实商户、真实退款和自动代付必须在上线前重新确认，不由 local 测试结果推导；本地允许财务登记人工退款异常，状态为 `OPEN → RESOLVED/REJECTED`，必须填写处理凭证/备注并写入审计日志，且不自动改变 `Payment` 或司机账本。

## 15. DriverAccount 与 DriverLedger

### DriverAccount

快照：

- available_balance
- frozen_balance
- version

### DriverLedger

不可缺失的流水：

- driver_id
- order_id / withdrawal_id
- ledger_type
- amount
- available_before / after
- frozen_before / after
- business_income_amount
- withdrawable_delta
- created_at

原则：任何余额变化必须有 Ledger；Ledger 不能因为业务修正而物理删除。

## 16. Withdrawal

状态建议：

```text
PENDING_REVIEW
├─ REJECTED
└─ APPROVED_PENDING_PAYMENT
      └─ PAID
```

申请时：

```text
available -= amount
frozen += amount
```

驳回时：

```text
frozen -= amount
available += amount
```

打款完成：

```text
frozen -= amount
```

记录审核人、打款人、计划和实际打款时间。

## 17. OperationLog

关键动作必须保存：

- operator_type / operator_id
- object_type / object_id
- action
- before_snapshot
- after_snapshot
- reason
- created_at

操作日志与核心业务修改应尽量在同一事务提交。

## 18. 异常场景矩阵

| 场景 | 建议行为 |
| --- | --- |
| 乘客取消与司机接受并发 | 仅一个事务成功，另一个返回最新状态 |
| 改派后原司机再接受 | 拒绝，Attempt 已失效 |
| 司机拒绝 | 记录原因，订单回待接单 |
| 司机定位 >5 分钟 | 不进入附近列表，可在全部司机中显示失效提示 |
| 支付页面关闭但已扣款 | 依赖服务端回调/主动查询修复 |
| 同一支付回调重复 | 幂等，不重复入账 |
| 线下收款录错 | 后台二次确认修正，保留前后值 |
| 提现审核驳回 | 冻结余额退回可用余额 |
| 司机弱网点击状态变化 | 未得到服务端确认前不显示最终成功 |
| 已完成订单再次操作 | 服务端拒绝非法状态跳转 |

## 19. PROPOSAL：立即单与预约单

V1.3 要求填写“出发时间”，但没有区分立即/预约筛选逻辑。

建议增加概念：

- IMMEDIATE：当前距离和位置时效强相关；
- SCHEDULED：当前距离仅作参考，应重点展示未来时间冲突和司机已有订单。

这是产品规则变化，未确认前不得擅自改变 V1.3 的 10km 规则。
