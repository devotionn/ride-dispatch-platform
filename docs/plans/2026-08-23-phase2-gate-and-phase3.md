# Phase 2 收口与 Phase 3 司机端垂直闭环实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完成 Phase 2 的真实浏览器验收闭环，并以最小可用 Android 司机端垂直切片进入 Phase 3。

**Architecture:** 继续使用 Spring Boot 模块化单体作为唯一业务真相，Passenger H5 和 Admin Web 通过稳定 API 联调。Phase 2 先建立可重复的浏览器验收和本地启动方式；Phase 3 再以登录、定位、待确认、接受/拒绝、履约推进为一条端到端链路，通知能力通过 Provider 抽象接入，不引入微服务或消息集群。

**Tech Stack:** Java 21、Spring Boot、Flyway、MySQL/H2、Vue 3、TypeScript、Vite、Vant、Element Plus、Kotlin、Jetpack Compose、Android 前台定位服务。

---

## 当前判断

- `docs/08-roadmap/development-roadmap-v1.0.md` 的 Phase 2 Gate 是：真实浏览器完成“乘客下单 → 后台出现 → 人工派司机”。
- Passenger H5 和 Admin Web 的 Phase 2 页面/API 已基本具备；两端构建已通过。
- 当前没有被纳入仓库的 Phase 2 浏览器 Gate：`.tmp_ui/full-flow.cjs` 主要从 Admin 页面开始，并通过 API 模拟司机，没有证明乘客 H5 下单。
- `passenger-h5/vite.config.ts` 当前代理到 8081，但后端默认端口和 README/Admin Web 都是 8080，导致本地闭环不可重复运行。
- 后端已经有订单、派单、履约、金额录入等核心 API，但 Payment、DriverAccount、DriverLedger、Withdrawal 尚未实现；这符合路线图中支付/结算后置的方向。
- `driver-app/` 已建立 Phase 3 最小 Android 工程，临时 JDK 17 + Gradle 8.9 + Android SDK 35 构建通过，当前没有已连接的 ADB 设备，仍未完成真机验证。
- `docs/09-decisions/PRD-V1.4-change-proposals.md` 仍是 PROPOSAL，尤其 CHANGE-002、004、005、006、010、011 不能被静默升级为正式需求。

## 约束

- 不把 PRD V1.4 Proposal 当成已确认需求；需要业务确认的条目先记录决策，再同步 PRD、领域、数据、API 和测试文档。
- 不在 Payment/Ledger 资金关系未确认前接入真实微信/支付宝或提现。
- 不用临时截图、临时 Node 项目或本机进程状态代替可重复验收。
- 每个阶段都必须有构建/测试结果和明确的 Gate，不以“页面能打开”作为完成标准。

### Task 1: 统一本地开发启动方式

**Files:**
- Modify: `passenger-h5/vite.config.ts`
- Modify: `passenger-h5/README.md`
- Modify: `admin-web/README.md`
- Create or retain: `server/src/main/resources/application-local.yml`
- Create or retain: `server/src/main/java/com/funccrypto/ridedispatch/LocalDevSeeder.java`

**Step 1: 固定端口约定**

以 Spring Boot 默认 `8080` 为后端端口，Passenger H5 和 Admin Web 的 `/api` 代理都指向 `http://localhost:8080`。如果确实要使用 8081，必须同时修改后端启动配置、两个前端代理和两份 README，不能只修改一个前端。

**Step 2: 固定本地数据启动方式**

为 `local` profile 写清楚 H2/MySQL 选择、Flyway 初始化、种子管理员和司机账号，以及地图未配置时使用手工坐标的方式。Seeder 只能挂在 `local` profile，不能污染测试或生产环境。

**Step 3: 验证启动链路**

Run: `docker compose -f deploy/docker-compose.dev.yml up -d mysql`（使用 MySQL 时）

Run: `mvn -B -ntp verify`（在安装 Maven 的 Java 21 环境中）

Run: 启动后端并检查 `GET /actuator/health`，再分别启动 `passenger-h5` 和 `admin-web`，确认 5173/5174 的 `/api` 请求都到同一个后端。

Expected: 后端健康检查成功，H5 和 Admin 登录/品牌读取不再依赖手工修改端口或碰巧存在的本机进程。

### Task 2: 建立可重复的 Phase 2 浏览器 Gate

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/phase2-passenger-admin.cjs`
- Create: `e2e/README.md`
- Modify: `.github/workflows/passenger-h5-ci.yml` or add a dedicated workflow after本地 Gate 稳定

**Step 1: 写浏览器失败用例**

使用 Playwright 从 `http://localhost:5173/ride` 打开公共 H5，走手工地图兜底填写 A/B 点、人数、出发时间和手机号并提交；读取订单号后打开 `http://localhost:5174`，登录 Admin，按订单号找到订单，打开详情并派给有效司机。

同时覆盖司机定向入口 `/ride/d/QRD101`：页面展示绑定司机、提交后状态为 `PENDING_DRIVER_CONFIRM`，不能让乘客手工切换司机。

**Step 2: 验证失败原因可诊断**

脚本在失败时保存页面截图、当前 URL、订单号和最后一次 API 响应摘要；禁止把完整 Passenger Token、密码或提现信息写入日志。

**Step 3: 实现最小脚本**

脚本只验证 Phase 2 Gate，不在脚本中直接改数据库，不用 API 替代 H5 创建订单；司机接受/拒绝可留给 Phase 3 的司机端测试。

**Step 4: 运行 Gate**

Run: `pnpm --dir e2e install --frozen-lockfile`

Run: `node e2e/phase2-passenger-admin.cjs`

Expected: 浏览器真实提交订单，Admin 能看到相同订单号、来源为 `PUBLIC_H5`，附近司机可见，人工派单后状态变为 `PENDING_DRIVER_CONFIRM`；定向入口订单来源为 `DRIVER_QR` 且初始状态正确。

**Step 5: 清理临时验证文件**

将 `.tmp_ui/` 和根目录 `passenger-order-status.png` 视为临时产物：有价值的测试逻辑迁移到 `e2e/` 后，不把临时脚本、截图、`node_modules` 纳入提交。

### Task 3: 按验收策略封闭 Phase 2

**Files:**
- Test: `e2e/phase2-passenger-admin.cjs`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/api/Phase1HttpFlowIntegrationTest.java`
- Modify if needed: `passenger-h5/src/views/RideCreateView.vue`
- Modify if needed: `passenger-h5/src/views/OrderStatusView.vue`
- Modify if needed: `admin-web/src/views/OrdersView.vue`
- Modify if needed: `docs/06-testing/acceptance-and-test-strategy-v1.0.md`

**Step 1: 对照 H5 验收项**

逐项验证 P-H5-001 至 P-H5-005：公共入口、司机定向入口、A 点定位/拒绝定位后的手工输入、无短信验证码、接单前取消。付款页 P-H5-006 暂不作为 Phase 2 Gate，因为路线图明确把 Payment 放在 Phase 4。

**Step 2: 对照 Admin 验收项**

验证三种来源筛选、后台代客建单、10km/5分钟/人数条件、直线距离排序、无附近司机时仍可从全部可接司机中人工选择、首次派单、待确认改派、强制操作原因和审计时间线。

**Step 3: 补齐高风险自动化测试**

至少覆盖：重复 `Idempotency-Key`、同 key 不同 body、Passenger Token 越权、改派后旧 Attempt 接受失败、乘客取消与司机接受并发、两个调度员同时派单。

**Step 4: 运行前端和后端 Gate**

Run: `pnpm --dir passenger-h5 build`

Run: `pnpm --dir admin-web build`

Run: `mvn -B -ntp verify`（Java 21 + Maven 环境）

Run: `node e2e/phase2-passenger-admin.cjs`

Expected: 前端构建 PASS、后端测试 PASS、浏览器 Gate PASS，且没有未解释的状态/审计数据不一致。

### Task 4: 把 Phase 2 结果和 P0 决策写回文档

**Files:**
- Modify: `README.md`
- Modify: `docs/08-roadmap/development-roadmap-v1.0.md`
- Create or modify: `docs/09-decisions/PRD-V1.4-change-proposals.md`
- Modify after confirmation: `docs/01-product/PRD-V1.3-baseline.md` or a formally versioned PRD V1.4
- Modify after confirmation: `docs/03-domain/domain-rules-v1.0.md`, `docs/04-data/data-model-v1.0.md`, `docs/05-api/api-contract-v1.0.md`, `docs/06-testing/acceptance-and-test-strategy-v1.0.md`

**Step 1: 更新当前状态**

只有在 Task 3 全部通过后，才把 README 的 Phase 2 标记为 Gate 通过，并把路线图“当前下一步”从 Phase 1 改为 Phase 3 Android。

**Step 2: 先确认 P0 决策**

在进入真实支付/结算前，必须确认 CHANGE-003（线下收款与可提现）、CHANGE-006（多次支付尝试）、CHANGE-010（最终金额锁定）、CHANGE-011（提现冻结）、CHANGE-020（商户与司机结算关系）。CHANGE-002（强制改派责任交接）虽然当前代码已经采用保守实现，也必须明确是否正式采纳。

**Step 3: 同步契约**

确认后的订单状态、金额、支付和提现规则必须同时落到领域规则、数据模型、API 和测试；不能只改实现或只改 Proposal 文件。

### Task 5: 以垂直切片启动 Phase 3 Android

**Files:**
- Create: `driver-app/settings.gradle.kts`
- Create: `driver-app/build.gradle.kts`
- Create: `driver-app/app/build.gradle.kts`
- Create: `driver-app/app/src/main/AndroidManifest.xml`
- Create: `driver-app/app/src/main/java/.../auth/*`
- Create: `driver-app/app/src/main/java/.../driver/*`
- Create: `driver-app/app/src/main/java/.../network/*`
- Create: `driver-app/app/src/test/java/.../*`

**Step 1: 建立最小 Compose 工程**

当前已建立工程骨架和第一版工作台；Debug APK 构建已通过，权限与真机验证待具备 ADB/设备后执行。

只包含登录、工作状态、当前可接人数、定位状态、待确认订单、活动订单和订单详情，不先做完整历史/收入/提现页面。

**Step 2: 接入已有司机 API**

按现有后端接口接入登录、`/driver/me/work-status`、`/driver/me/available-passengers`、`/driver/me/location`、`/driver/orders/pending-confirmation`、`/driver/dispatch-attempts/{id}/accept|reject`、`/driver/orders/{orderNo}/progress` 和最终金额接口。服务端确认前，UI 不显示关键动作成功。

**Step 3: 实现前台定位服务**

按目标 Android SDK 处理前台服务和权限；只在可接单状态按配置周期上报，服务端继续以 `locatedAt <= 5 分钟` 判断有效性。断网时短暂缓存并恢复上传，但不本地伪造订单状态。

**Step 4: 实现通知抽象**

后端保留 `NotificationService`/`PushProvider` 接口，前台实时刷新作为补充，Push/系统通知作为后台和锁屏主通道；通知失败不能回滚订单创建，后台必须仍能看到待确认订单。

**Step 5: 验证 Phase 3 Gate**

使用真实 Android 设备跑通：H5 下单 → Admin 派单 → 司机前台/后台/锁屏收到提醒 → 接受 → 四段履约 → 到达目的地 → 录入金额。弱网、重复点击、派单失效和司机拒绝必须有明确状态提示。

### Task 6: Phase 4 之前实现支付领域，不直接接真实渠道

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/*`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/*`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/withdrawal/*`
- Create: `server/src/main/resources/db/migration/V005__payment_and_settlement.sql`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/payment/*`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/settlement/*`

**Step 1: 先实现 MockPaymentProvider**

建立 `Payment`/`PaymentAttempt`，支持微信失败后支付宝成功，但同一订单只能成功结算一次；第三方流水号、金额和回调必须有唯一约束和幂等测试。

**Step 2: 再实现 Ledger/Withdrawal 状态机**

所有余额变化追加 Ledger；提现申请时 available 转 frozen，驳回解冻，人工打款核销 frozen；线下直接收款默认业务收入增加、可提现余额不增加，具体以业务确认结果为准。

**Step 3: 完成资金不变量测试后再接 Provider**

覆盖重复回调、金额不一致、支付页面关闭后回调、线下收款、两次提现并发、恢复备份后的账本核对。只有这些通过且 CHANGE-020 已确认，才进入真实商户/沙箱接入。

## 完成标准

1. Phase 2 浏览器 Gate 可在新环境重复运行，不依赖临时进程、临时截图或手工改代理。
2. Passenger H5、Admin Web 和 Backend 的构建/测试结果可追踪。
3. P0 业务决策不再停留在“代码默认行为”层面。
4. Phase 3 以真实 Android 设备闭环，而不是只完成 Compose 页面。
5. Payment、Ledger、Withdrawal 在真实支付前具备数据模型、幂等、审计和恢复验证。
