# Local Mock Payment and Settlement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不依赖云服务器和真实支付商户的前提下，完成并验证“待收款 → 模拟支付/线下收款 → 已完成 → 司机收入账本 → 提现冻结/审核/打款”的本地闭环。

**Architecture:** 保留现有订单履约主链路，新增独立的 `Payment`/`PaymentAttempt` 支付领域和 `DriverAccount`/`DriverLedger`/`Withdrawal` 结算领域。支付渠道通过 `PaymentProvider` 接口抽象，第一阶段只提供 local profile 下的 `MockPaymentProvider`；订单完成、账本入账和提现余额变化都由服务端事务执行，前端不能直接伪造成功。业务界面继续使用“元”，API、数据库和账本统一使用整数“分”。

**Tech Stack:** Spring Boot 4.1、Spring Data JPA、Flyway、H2 local profile、MySQL schema compatibility、JUnit/Spring Boot integration tests、Node fetch HTTP depth tests、Vue 3/Vite、Kotlin Compose Android。

---

## 0. 先冻结本地实现口径

这一步不改代码，先把尚未确认的资金规则固定成可测试的 local 默认值；真实商户上线前仍必须重新确认 CHANGE-003 和 CHANGE-020。

**Local 默认规则：**

- 平台抽佣为 0；
- 线上 Mock 支付：计入司机业务收入，并增加同额可提现余额；
- 司机线下收款：计入业务收入，但 `withdrawableDelta = 0`，避免司机已经拿到现金后再次提现；
- 一个订单只允许一个 Payment 成功结算；一个 Payment 可以有多个支付尝试；
- 支付成功后金额和支付渠道锁定；金额修正只允许未支付的线下异常流程；
- 不做真实退款和自动代付，只保留人工异常/人工打款状态；
- 所有 Mock 成功接口只在 `local` profile 注册，默认/生产 profile 不暴露。

**停止条件：** 如果甲方要求线下收款也增加可提现余额，先修改结算策略和验收用例，再进入实现；不能在代码里悄悄改变资金口径。

## 1. 盘点当前边界并建立失败测试

**Files:**

- Modify: `docs/01-product/PRD-V1.3-baseline.md`
- Modify: `docs/05-api/api-contract-v1.0.md`
- Modify: `docs/03-domain/domain-rules-v1.0.md`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/payment/PaymentServiceIntegrationTest.java`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/settlement/SettlementServiceIntegrationTest.java`
- Modify: `e2e/local-http-depth.cjs`

**Steps:**

1. 在 PRD、领域规则和 API 文档中写明上述 local 规则、金额单位、支付状态和提现状态。
2. 先写失败测试，覆盖：完成订单后不存在 Payment、重复成功回调会重复结算、不同金额回调被接受、线下收款增加可提现余额、提现并发超额成功。
3. 运行 `mvn -f server/pom.xml -Dtest=PaymentServiceIntegrationTest,SettlementServiceIntegrationTest test`。
4. 预期：测试因为领域对象/服务/表不存在而失败；不得先写实现再补测试。

## 2. 设计 Payment 数据模型和迁移

**Files:**

- Create: `server/src/main/resources/db/migration/V005__payment_domain.sql`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentAttemptEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentStatus.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentAttemptStatus.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentChannel.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentRepository.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentAttemptRepository.java`

**Schema contract:**

- `payment`: `id`, `payment_no`, `order_id`, `amount`, `status`, `settlement_method`, `access_token_hash`, `access_token_created_at`, `expires_at`, `created_at`, `settled_at`, `version`；
- `payment_attempt`: `id`, `attempt_no`, `payment_id`, `channel`, `merchant_order_no`, `third_party_transaction_no`, `amount`, `status`, `callback_payload_digest`, `created_at`, `paid_at`, `updated_at`；
- `payment.order_id` 唯一；`payment.payment_no` 唯一；`payment.access_token_hash` 唯一；
- `payment_attempt.attempt_no`、`merchant_order_no` 唯一；第三方流水号非空时唯一；
- 金额使用 `BIGINT` 分；不使用浮点；
- Payment/Attempt 只追加状态和时间，不物理删除；
- 通过外键绑定 `ride_order`，禁止 Payment 指向不存在订单。

**Steps:**

1. 先完成 Flyway 表和索引。
2. 编写 Entity 的状态枚举和受保护的状态转换方法。
3. 运行 `mvn -f server/pom.xml -Dspring.profiles.active=local test`，确认 Flyway + JPA validate 通过。
4. 提交：`feat: add payment domain schema and entities`。

## 3. 实现最终金额到 Payment 的事务边界

**Files:**

- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/RideOrderEntity.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/OrderManagementService.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentTokenService.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentService.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/api/PassengerOrderResponse.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/PublicOrderService.java`

**Behavior:**

- 司机提交最终金额时，在同一个事务内完成：订单进入 `PENDING_PAYMENT`、创建唯一 Payment、生成一次性原始付款 Token（只保存 hash）；
- 已有 Payment 或订单不是 `IN_SERVICE + ARRIVED_DESTINATION` 时拒绝重复创建；
- Passenger access token 只能读取自己订单的付款上下文；原始 payment token 不写日志、不进入数据库明文；
- `PassengerOrderResponse` 在 `PENDING_PAYMENT` 时返回 `paymentToken`、`paymentStatus` 和金额，其他状态不暴露无效支付入口；
- Payment 创建失败时整个金额提交事务回滚，不能留下“待付款但无 Payment”的订单。

**Tests:**

- 金额提交创建 Payment；
- 同一订单重复提交被拒绝；
- 其他乘客 Token 不能读取付款上下文；
- 付款 Token 不能跨订单使用；
- 服务端保存 120000 分，前端显示 ¥1200.00。

## 4. 建立 Provider 和 Mock 支付回调

**Files:**

- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentProvider.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/MockPaymentProvider.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/PublicPaymentController.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/LocalMockPaymentController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/auth/SecurityConfig.java`
- Modify: `server/src/main/resources/application-local.yml`

**API proposal:**

- `GET /api/v1/public/payments/{paymentToken}`：读取付款上下文和当前状态；
- `POST /api/v1/public/payments/{paymentToken}/attempts`：创建一次支付尝试，body 为 `{ "channel": "MOCK_WECHAT" }`；
- `POST /api/v1/local/mock-payments/{attemptNo}/success`：local profile 下模拟第三方成功回调；
- `POST /api/v1/local/mock-payments/{attemptNo}/failure`：local profile 下模拟失败回调；
- `GET /api/v1/public/payments/{paymentToken}/status`：页面关闭后轮询服务端状态。

**安全规则:**

- Local mock controller 使用 `@Profile("local")`；默认配置不加载；
- Mock 回调只能使用 attempt 编号和服务端保存的金额，客户端不能传入可覆盖的订单金额；
- 成功回调必须校验 Payment、Attempt、channel、merchant order、amount 和订单状态；
- 回调重复提交返回幂等成功，不新增 Attempt、不重复完成订单、不重复写 Ledger；
- 失败回调只结束当前 Attempt，允许创建第二次 Attempt；
- Payment 已成功后所有其他 Attempt 回调进入 `IGNORED_ALREADY_SETTLED` 或等价可审计结果。

**Tests:**

- Mock 成功闭环；
- 微信失败后支付宝/第二次 Mock 成功；
- 同一回调重复 2 次；
- 回调金额不一致；
- 回调第三方流水号重复；
- 付款页面关闭后重新查询仍能显示最终状态；
- 支付 Token 过期和已支付后的访问行为。

## 5. 将线上支付和线下收款接入订单完成规则

**Files:**

- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/RideOrderEntity.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/api/DriverOrderController.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/OfflinePaymentController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/auth/SecurityConfig.java`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/payment/OfflinePaymentIntegrationTest.java`

**Behavior:**

- `POST /api/v1/driver/orders/{orderNo}/offline-payment/confirm` 只允许当前司机、订单 `PENDING_PAYMENT`、金额已锁定时调用；
- 线下收款要求 request id/二次确认字段，服务端记录确认时间和操作者；
- 线上支付成功和线下收款确认都只能把订单从 `PENDING_PAYMENT` 变成 `COMPLETED` 一次；
- 取消、已完成、金额未提交的订单都拒绝收款确认；
- 线上与线下不能对同一订单重复完成；
- 所有关键动作写 `OperationLog`，包含订单、Payment、Attempt、原因和 request id。

## 6. 建立 DriverAccount、DriverLedger、Withdrawal

**Files:**

- Create: `server/src/main/resources/db/migration/V006__driver_settlement.sql`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/DriverAccountEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/DriverLedgerEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/WithdrawalEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/WithdrawalStatus.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/LedgerType.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/DriverAccountRepository.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/DriverLedgerRepository.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/WithdrawalRepository.java`

**Invariants:**

- 每个司机只有一条 Account；`available_balance >= 0`、`frozen_balance >= 0`；
- Account 每次变化增加 version，并使用 `findByDriverIdForUpdate` 行锁；
- 每个余额变化必须有不可删除 Ledger；
- Ledger 保存 `available_before/after`、`frozen_before/after`、`business_income_amount`、`withdrawable_delta`、`event_key`；
- `event_key` 唯一，使用 `PAYMENT:{paymentNo}:SUCCESS`、`OFFLINE:{orderNo}:COLLECTED`、`WITHDRAWAL:{withdrawalNo}:RESERVE/REJECT/PAID`；
- 提现状态：`PENDING_REVIEW → APPROVED_PENDING_PAYMENT → PAID`，驳回为 `REJECTED`；
- 申请提现：available 减、frozen 加；驳回：frozen 减、available 加；打款：frozen 减；
- 金额不足、重复审核、重复打款、并发提现都必须拒绝或幂等返回。

**Settlement service:**

- `SettlementService.recordOnlinePaymentIncome(payment)`：业务收入 + 可提现余额；
- `SettlementService.recordOfflineIncome(order)`：业务收入 + 0 可提现余额；
- `WithdrawalService.request/approve/reject/markPaid(...)`：所有操作与 Ledger、审计在同一事务内完成。

## 7. 提供司机和财务 API

**Files:**

- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/api/DriverSettlementController.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/api/AdminSettlementController.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/AdminPaymentController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/auth/SecurityConfig.java`

**Driver API:**

- `GET /api/v1/driver/me/account`
- `GET /api/v1/driver/me/ledger`
- `GET /api/v1/driver/me/withdrawals`
- `POST /api/v1/driver/me/withdrawals`
- `POST /api/v1/driver/orders/{orderNo}/offline-payment/confirm`

**Admin/Finance API:**

- `GET /api/v1/admin/payments`
- `GET /api/v1/admin/payments/{paymentNo}`
- `GET /api/v1/admin/withdrawals`
- `POST /api/v1/admin/withdrawals/{withdrawalNo}/approve`
- `POST /api/v1/admin/withdrawals/{withdrawalNo}/reject`
- `POST /api/v1/admin/withdrawals/{withdrawalNo}/mark-paid`

**权限:**

- DRIVER 只能读取自己的账户、流水、提现和自己的线下收款；
- ADMIN/DISPATCHER 读取订单支付状态；
- FINANCE/ADMIN 审核提现、标记人工打款、查看账本；
- 司机不能调用 Mock 回调、修改 Ledger 或直接改余额。

## 8. 扩展 Passenger H5 支付页

**Files:**

- Create: `passenger-h5/src/api/payments.ts`
- Modify: `passenger-h5/src/domain/types.ts`
- Modify: `passenger-h5/src/api/orders.ts`
- Create: `passenger-h5/src/views/PaymentView.vue`
- Modify: `passenger-h5/src/router.ts`
- Modify: `passenger-h5/src/views/OrderStatusView.vue`
- Modify: `passenger-h5/src/styles.css`

**UI flow:**

1. `PENDING_PAYMENT` 订单展示“去付款”；
2. 付款页显示订单、路线、金额（元）、支付状态和可用 Mock 渠道；
3. 点击渠道创建 Attempt，显示“等待支付”；
4. Local 模式显示“模拟支付成功/失败”按钮，生产构建不显示；
5. 页面每 2 秒轮询，最多 60 秒；关闭页面后重新打开仍从服务端恢复；
6. `PAID/COMPLETED` 显示完成态，禁止再次支付；
7. 金额格式统一 `¥${fen / 100}`，禁止前端直接修改金额。

## 9. 扩展 Driver App 和 Admin Web

**Driver files:**

- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/network/DriverApi.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/domain/Models.kt`
- Modify: `driver-app/app/src/test/java/com/funccrypto/ridedispatch/driver/domain/DriverFlowTest.kt`

**Driver behavior:**

- 待收款卡片增加“线下已收款”按钮和二次确认；
- 收款成功后刷新订单，卡片进入已完成/历史；
- 增加“收入/账户/提现”折叠区，分别显示业务收入、可提现、冻结；
- 提现金额使用元输入，提交时转分；
- 异常提示必须来自服务端错误码，不在客户端乐观修改余额。

**Admin files:**

- Create: `admin-web/src/api/payments.ts`
- Create: `admin-web/src/api/settlements.ts`
- Create: `admin-web/src/views/PaymentsView.vue`
- Create: `admin-web/src/views/WithdrawalsView.vue`
- Modify: `admin-web/src/router.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`
- Modify: `admin-web/src/domain/types.ts`

**Admin behavior:**

- 支付列表能看 Payment、Attempt、渠道、金额、状态、流水号；
- 提现列表支持审核、驳回原因、标记已打款；
- 所有金额以元显示，所有操作后重新请求服务端；
- 只显示当前角色有权限的财务入口。

## 10. 自动化测试矩阵

**Server unit/integration:**

- Payment 创建、状态机、Token hash、过期；
- Attempt 多次尝试、第三方流水号唯一、金额不一致；
- 回调重复、并发回调、支付成功后金额锁定；
- 线上成功/线下确认只完成一次；
- Ledger 余额前后快照和 event key 幂等；
- 提现余额不足、并发超额、驳回解冻、打款核销；
- DRIVER/FINANCE/ADMIN 越权。

**HTTP depth:**

- Extend `e2e/local-http-depth.cjs` from 13 to at least 25 assertions：
  - payment token access;
  - create attempt;
  - fail then retry;
  - duplicate success callback;
  - amount mismatch;
  - offline confirmation;
  - account/ledger invariant;
  - withdrawal reserve/reject/paid;
  - concurrent withdrawal.

**Browser:**

- Extend `e2e/phase2-passenger-admin.cjs` or add `e2e/payment-settlement-browser.cjs`：
  - H5 付款页；
  - Mock 成功/失败/重复点击；
  - Admin 支付详情和提现审核；
  - 金额展示元。

**Android:**

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug -PdriverApiBaseUrl=http://10.0.2.2:8081`
- Emulator flow：登录 → 接单 → 四阶段 → 输入 1200 元 → 线下确认 → 订单已完成；
- 验证重复点击按钮禁用、异常刷新、账户金额展示和提现边界。

## 11. 本地验收 Gate

只有以下全部通过，才认为本阶段完成：

1. `mvn -f server/pom.xml verify` 全部通过；
2. `e2e/local-http-depth.cjs` 至少 25/25 PASS；
3. H5 付款页能够从订单状态页进入，Mock 成功后订单变 `COMPLETED`；
4. 重复回调、金额不一致、支付 Token 越权均被拒绝或幂等处理；
5. 线上成功、线下收款、提现冻结的 Ledger 均能逐笔解释余额；
6. 并发提现不会出现负余额或重复冻结；
7. Admin、Driver、Passenger 三端金额均按元展示；
8. Android Emulator 完成线下收款和账户读取；
9. Mock 接口在非 local profile 下不存在；
10. 更新 `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`，记录每个 Gate 的命令和结果。

## 12. 风险、回滚与不做事项

- **真实资金关系未确认：** local 只使用默认策略；不接入真实微信/支付宝、不自动打款。
- **支付与结算耦合风险：** Payment 成功只调用 SettlementService，不把余额字段塞进订单；支付失败不写收入 Ledger。
- **历史测试数据：** 新增迁移后不自动乘金额、不修改已存在订单；本地 H2 重启可清空测试数据，生产禁止清库。
- **H2/MySQL 差异：** 必须使用唯一约束、行锁和整数金额；完成 local 后补 MySQL/Docker 验证。
- **回调安全：** Mock controller 仅 local profile；真实 Provider 需要验签、商户号和第三方流水号校验。
- **范围控制：** 本计划不包含自动计价、真实支付商户接入、自动退款、自动代付、实时轨迹、Push 厂商适配和云服务器部署。

## 13. 建议提交节奏

每个 Gate 单独提交，便于回滚：

1. `docs: freeze local payment and settlement rules`
2. `feat: add payment domain schema`
3. `feat: create payment context after final amount`
4. `feat: add local mock payment provider`
5. `feat: complete orders from online or offline payment`
6. `feat: add driver account and immutable ledger`
7. `feat: add withdrawal review lifecycle`
8. `feat: add passenger mock payment page`
9. `feat: add driver offline collection and settlement views`
10. `test: cover local payment and settlement gates`

Plan complete and saved to `docs/plans/2026-08-24-local-payment-settlement.md`. Implementation should start only after the local settlement policy in Section 0 is accepted.
