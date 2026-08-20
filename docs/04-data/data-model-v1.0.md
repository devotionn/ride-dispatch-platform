# 数据模型设计 V1.0

- 状态：DRAFT
- 数据库：MySQL 8.x
- 原则：先满足约 300 司机规模下的数据正确性和可维护性，不提前为超大规模做复杂分库分表。

## 1. 通用约定

- 主键：优先 `BIGINT` 或稳定的全局业务 ID；对外业务编号与数据库主键分离。
- 时间：数据库统一明确时区策略，API 统一 ISO-8601；业务展示再转换。
- 金额：`BIGINT` 保存最小货币单位，禁止 FLOAT/DOUBLE。
- 并发：核心聚合增加 `version`。
- JSON：仅适合审计快照/第三方扩展信息，不用 JSON 代替应查询的核心关系字段。
- 财务、审计、派单历史不物理删除。

## 2. platform_brand

建议字段：

- id
- company_name
- logo_url
- updated_by
- updated_at

首期可只保留单行有效配置。

## 3. admin_user / admin_role / admin_user_role

### admin_user

- id
- username
- password_hash
- display_name
- mobile
- status
- last_login_at
- created_at / updated_at

### admin_role

首期角色可初始化：ADMIN / DISPATCHER / FINANCE。

## 4. driver

建议字段：

- id
- driver_no（唯一）
- name
- mobile
- password_hash / auth identifier
- account_status
- work_status
- max_passengers
- available_passengers
- qr_short_code（唯一）
- default_vehicle_id
- created_at / updated_at
- version

索引：driver_no、mobile、qr_short_code、account_status + work_status。

## 5. vehicle

建议字段：

- id
- driver_id / ownership relation
- plate_no
- brand_model
- max_passengers
- status
- created_at / updated_at

生产资质字段如果确认需要，使用单独扩展字段/表，不把未知监管协议写死。

## 6. driver_location_current

建议字段：

- driver_id（唯一）
- latitude
- longitude
- accuracy_meters
- source
- located_at
- received_at

原则：

- 附近司机判断看 `located_at`；
- 每次新位置覆盖当前快照；
- 是否另存历史位置由生产运营/合规需求确认，不因 PRD“不展示实时轨迹”而混淆。

## 7. orders

建议字段：

- id
- order_no（唯一）
- source_type
- source_driver_id（可空）
- current_driver_id（可空）
- passenger_mobile
- passenger_access_token_hash / token identifier
- pickup_address
- pickup_latitude / pickup_longitude
- destination_address
- destination_latitude / destination_longitude
- passenger_count
- departure_at
- remark
- status
- trip_stage
- final_amount
- settlement_method
- created_at
- accepted_at
- service_started_at
- arrived_destination_at
- completed_at
- cancelled_at
- version

索引建议：

- unique(order_no)
- status + departure_at
- current_driver_id + status
- source_driver_id + created_at
- created_at

## 8. order_progress_event

追加式履约事件：

- id
- order_id
- stage
- operator_type
- operator_id
- occurred_at
- latitude / longitude（可选）
- remark

索引：order_id + occurred_at。

## 9. dispatch_attempt

- id
- order_id
- target_driver_id
- dispatch_type
- status
- dispatched_by
- dispatched_at
- responded_at
- reject_reason_code
- reject_reason_text
- reassign_from_driver_id
- reassign_reason
- invalidated_at
- version

关键约束：

数据库层无法简单用普通唯一索引表达“一个订单最多一个 WAITING”时，应由事务 + 锁/版本 + 测试保证，并尽可能配合适用的唯一业务键。

索引：

- order_id + dispatched_at
- target_driver_id + status + dispatched_at
- status + dispatched_at

## 10. payment

表示订单应收款：

- id
- payment_no（唯一）
- order_id（原则上一个有效应收）
- amount
- status
- settled_method
- settled_at
- created_at / updated_at
- version

## 11. payment_attempt

- id
- payment_id
- channel
- merchant_order_no（唯一）
- third_party_transaction_no（成功后唯一）
- amount
- status
- requested_at
- callback_at
- paid_at
- provider_payload_digest / provider_extra

关键约束：

- third_party_transaction_no 唯一；
- merchant_order_no 唯一；
- 成功处理必须幂等；
- callback 不直接根据客户端传参确认成功。

## 12. payment_adjustment

用于受控记录线下收款等人工修正：

- id
- payment_id / order_id
- before_amount
- after_amount
- delta_amount
- reason
- operator_id
- created_at

不允许把修正历史通过 UPDATE 抹掉。

## 13. driver_account

- driver_id（唯一）
- available_balance
- frozen_balance
- updated_at
- version

余额快照只用于高效展示和事务控制，真实可追溯性依赖 Ledger。

## 14. driver_ledger

- id
- driver_id
- order_id（可空）
- withdrawal_id（可空）
- ledger_type
- business_income_amount
- withdrawable_delta
- available_before
- available_after
- frozen_before
- frozen_after
- reference_no
- remark
- created_at

索引：driver_id + created_at；order_id；withdrawal_id；reference_no。

需要设计业务唯一 reference，避免重复支付回调重复生成 Ledger。

## 15. withdrawal

- id
- withdrawal_no（唯一）
- driver_id
- amount
- receiving_method
- receiving_account_masked / encrypted payload
- status
- requested_at
- reviewed_by / reviewed_at
- reject_reason
- planned_payment_at
- paid_by / paid_at
- payment_reference
- version

索引：driver_id + requested_at；status + requested_at。

## 16. operation_log

- id
- operator_type
- operator_id
- object_type
- object_id
- action
- before_json
- after_json
- reason
- request_id / trace_id
- created_at

索引：object_type + object_id + created_at；operator_id + created_at；action + created_at。

## 17. system_config

可用于可配置参数，例如：

- 位置失效阈值（V1.3 默认 5 分钟，若允许配置必须明确哪些是产品固定规则）
- 新单提示阈值
- 上传策略参数
- 文件/品牌相关非敏感配置

敏感密钥不得放普通配置表明文保存。

## 18. 核心关系

```text
Driver 1 ── N Vehicle
Driver 1 ── 1 DriverLocationCurrent
Driver 1 ── 1 DriverAccount
Driver 1 ── N DriverLedger
Driver 1 ── N Withdrawal

Order 1 ── N DispatchAttempt
Order 1 ── N OrderProgressEvent
Order 1 ── 1 Payment
Payment 1 ── N PaymentAttempt
Payment 1 ── N PaymentAdjustment
```

## 19. 数据不变量

必须用数据库约束 + 应用事务 + 自动化测试共同保证：

1. 订单号唯一。
2. 司机二维码短码唯一。
3. 一个支付成功事件不能重复入账。
4. 一个第三方支付流水不能绑定多个业务成功记录。
5. 余额变化必有 Ledger。
6. available_balance 和 frozen_balance 不允许为负（除非未来业务明确支持透支）。
7. 已失效 DispatchAttempt 不得重新生效。
8. 已完成/已取消订单不得无审计重开。
9. driver.available_passengers 不得超过车辆最大允许人数。

## 20. 敏感信息

建议：

- 密码只保存强哈希。
- 提现账号按“展示掩码 + 加密原值”设计，日志中禁止输出完整账号。
- passenger access token 不建议明文长期存储，可存哈希/可轮换标识。
- API/业务日志默认脱敏手机号、支付账号、令牌。

## 21. 迁移

所有数据库结构通过 Flyway 版本化：

```text
V001__baseline_schema.sql
V002__add_xxx.sql
```

生产环境禁止手工改表后不补迁移脚本。