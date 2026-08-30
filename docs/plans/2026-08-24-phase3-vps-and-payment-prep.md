# Phase 3 Android、云服务器部署与支付前置准备实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不提前接入真实支付的前提下，把已通过本地验收的司机端链路收口为可重复运行、可部署到云服务器的 Phase 3 版本，并为后续支付/结算实现建立明确决策门。

**Architecture:** 继续使用 Spring Boot 模块化单体作为唯一业务真相；Passenger H5 和 Admin Web 构建为静态资源并由 Web 服务器提供，Driver App 通过 HTTPS API 访问后端。开发环境保留 H2 `local` profile，云服务器使用 MySQL + Flyway；不引入微服务、消息集群或真实支付渠道。

**Tech Stack:** Java 21、Spring Boot、Flyway、MySQL、Vue 3、TypeScript、Vite、pnpm、Playwright、Kotlin、Jetpack Compose、Gradle 8.9、Android SDK 35、Docker Compose、Nginx/HTTPS。

---

## 当前基线与边界

- 后端 `mvn verify` 已通过，现有 HTTP 集成测试已覆盖“创建订单 → 派单 → 接受 → 四段履约 → 录入最终金额 → `PENDING_PAYMENT`”。
- Passenger H5、Admin Web 构建已通过，`e2e/phase2-passenger-admin.cjs` 已通过。
- Pixel 7 Android Emulator 已可运行；Driver App 已完成 D101 登录、接单、四段履约和最终金额录入，实测订单进入 `PENDING_PAYMENT`。
- 当前 `PENDING_PAYMENT` 是本阶段预期终点，不是故障：Passenger H5 目前只有金额展示，没有支付接口；支付、账本、提现不进入本轮实现。
- 本机后端当前为 `8081` 是因为 `8080` 被用户已有 Python 服务占用；计划默认约定仍为后端 `8080`，任何端口冲突必须提示并由用户决定，禁止脚本误杀未知进程。
- 真机、后台/锁屏通知、生产 HTTPS、备份恢复和支付领域仍未完成。

## 执行顺序与 Gate

```text
Gate A：本地可重复启动
  → Gate B：Android Phase 3 模拟器/真机专项
  → Gate C：云服务器 staging 部署与恢复演练
  → Gate D：支付/结算业务决策确认
  → Gate E：Mock 支付、Ledger、Withdrawal 领域实现
```

每个 Gate 都必须有命令、日志或截图等可复核证据；“页面能打开”不算通过。

---

### Task 1: 固化当前基线与验收记录

**Files:**
- Modify: `README.md`
- Modify: `docs/06-testing/acceptance-and-test-strategy-v1.0.md`
- Modify: `driver-app/README.md`
- Create: `docs/08-roadmap/phase3-execution-log.md`

**Step 1: 写入已通过的基线**

记录以下事实和日期：后端测试通过、两个 Web 构建通过、Phase 2 浏览器 Gate 通过、Android Emulator 端到端到达 `PENDING_PAYMENT`。

**Step 2: 明确未完成项**

单独列出 Push、后台/锁屏、真实设备、服务器、HTTPS、支付、账本、提现，不把它们写成“已完成”。

**Step 3: 建立执行日志模板**

为每个 Gate 预留：命令、环境、结果、失败证据、修复提交和回归命令。

**Step 4: 验证文档不互相矛盾**

Run: `rg -n "当前下一步|Gate 状态|PENDING_PAYMENT|支付二维码|真实设备" README.md docs driver-app`

Expected: Phase 2 已标记 PASS，Phase 3 为当前工作，支付仍明确后置。

---

### Task 2: 建立不依赖临时进程的本地启动方式

**Files:**
- Create: `scripts/dev/start-local.ps1`
- Create: `scripts/dev/stop-local.ps1`
- Create: `scripts/dev/README.md`
- Modify: `passenger-h5/.env.example`
- Modify: `admin-web/.env.example`
- Modify: `passenger-h5/README.md`
- Modify: `admin-web/README.md`
- Modify: `.gitignore`

**Step 1: 统一端口和环境变量**

约定后端 `8080`、Passenger H5 `5173`、Admin Web `5174`；脚本支持 `BACKEND_PORT` 覆盖，并把同一端口写入两个 Vite 的 `VITE_DEV_API_TARGET`。脚本检测到端口被未知进程占用时只报错，不结束该进程。

**Step 2: 固化 local profile 启动**

脚本使用 `server` 的 `local` profile，保留 H2 和演示账号；不允许 `LocalDevSeeder` 在 test/production profile 激活。

**Step 3: 记录并清理子进程**

启动脚本把 PID 写入 `.tmp/ride-dispatch-local/`，停止脚本只停止自己记录的进程，并在退出时保留日志。

**Step 4: 增加启动健康检查**

Run: `.\scripts\dev\start-local.ps1`

Run: `Invoke-RestMethod http://localhost:8080/actuator/health`

Run: `Invoke-WebRequest http://localhost:5173/ride`

Run: `Invoke-WebRequest http://localhost:5174/login`

Expected: 三个地址都成功；脚本能在新 PowerShell 窗口重复启动，不依赖手工修改代理或碰巧存在的服务。

**Step 5: 回归 Phase 2 Gate**

Run: `$env:PASSENGER_URL='http://localhost:5173'; $env:ADMIN_URL='http://localhost:5174'; pnpm --dir e2e run phase2`

Expected: 浏览器真实创建订单、Admin 派单和司机定向入口断言全部 PASS。

---

### Task 3: 收口 Driver App 的状态、错误和生命周期处理

**Files:**
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/network/DriverApi.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/domain/Models.kt`
- Modify: `driver-app/app/src/test/java/com/funccrypto/ridedispatch/driver/domain/DriverFlowTest.kt`

**Step 1: 先补状态规则测试**

覆盖非法履约阶段、`PENDING_PAYMENT` 不再显示“推进”按钮、最终金额只能是正整数、重复点击期间动作按钮保持禁用。

**Step 2: 统一刷新和动作状态**

将加载、动作中、刷新失败、最近刷新时间收敛到可测试状态；动作成功后只重新拉取服务端状态，不在客户端提前伪造成功。

**Step 3: 处理鉴权失效和网络错误**

HTTP 401 清理会话并回到登录页；超时、断网、服务端业务错误显示可读错误码和重试入口，不能吞掉错误后显示旧状态。

**Step 4: 改为生命周期感知轮询**

页面进入前台时每 15 秒刷新；离开页面、退出登录或 Activity 销毁时停止轮询，避免重复协程和旧 Token 请求。

**Step 5: 运行 Android 单测和构建**

Run: `$env:JAVA_HOME='C:\Users\sudsp\AppData\Local\Temp\ride-dispatch-android-toolchain\jdk17'; .\driver-app\gradlew.bat -p driver-app testDebugUnitTest assembleDebug`

Expected: 新增规则测试和现有测试全部 PASS，Debug APK 生成。

---

### Task 4: 完成定位权限与后台通知的 Phase 3 专项

**Files:**
- Modify: `driver-app/app/src/main/AndroidManifest.xml`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/location/LocationForegroundService.kt`
- Create: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/notification/DriverNotificationManager.kt`
- Modify: `driver-app/app/build.gradle.kts`
- Modify: `driver-app/README.md`

**Step 1: 验证 Android 13+ 通知权限**

首次启用接单时请求 `POST_NOTIFICATIONS`；拒绝时仍允许查看订单，但明确说明无法收到后台提醒。

**Step 2: 固化前台定位服务规则**

只有服务端确认 `AVAILABLE` 后启动定位服务；暂停/下线/退出登录/权限撤销时停止；服务重启不能使用过期 Token，也不能把定位失败当作接单成功。

**Step 3: 增加本地提醒去重**

在没有 Push Provider 前，以前台服务/轮询为临时联调提醒通道；按 `attemptId` 去重，点击通知回到工作台。明确标注这不是生产 Push 替代品。

**Step 4: 加入可观测信息**

工作台显示定位权限、服务运行和最近上报时间；定位上传失败只提示状态，不伪造成功。

**Step 5: 用 Emulator 验证前台/后台行为**

Run: 安装 APK，授予定位/通知权限，切换“开始接单/暂停/下线”，使用 `adb shell dumpsys activity services` 和后端司机位置接口核对服务状态。

Expected: 权限和服务状态一致；定位时间新鲜时附近司机可见，服务停止后不再继续上报。

**Step 6: 保留真机 Gate**

真机到位前不得宣称后台、锁屏、省电策略和厂商 Push 已通过；先用 Emulator 完成开发回归，服务器购买后再做 staging 真机测试。

---

### Task 5: 把 Driver App 纳入可重复 CI

**Files:**
- Create: `.github/workflows/driver-app-ci.yml`
- Modify: `driver-app/README.md`
- Modify: `driver-app/gradle/wrapper/gradle-wrapper.properties` only if checksum/version needs correction

**Step 1: 配置 Java 17 和 Android SDK 35**

CI 使用固定 JDK 17、Gradle Wrapper 和 Android SDK 35，不依赖开发机路径。

**Step 2: 配置依赖缓存和构建任务**

Run in CI: `./driver-app/gradlew -p driver-app testDebugUnitTest assembleDebug`

**Step 3: 上传 Debug APK 和测试报告**

仅在 CI 产物中保留 APK、单测报告和构建摘要，不提交 `build/`、`.gradle/` 或本地 SDK。

**Step 4: 验证分支 Gate**

Expected: 修改 `driver-app/**` 时触发工作流；无 Android 代码变更时不额外消耗构建资源。

---

### Task 6: 准备云服务器 staging 部署包

**前置条件（服务器购买后才执行）:**

- Ubuntu 22.04/24.04 或等价 Linux；SSH sudo 权限；固定公网 IP；
- 域名及 DNS 控制权；
- 服务器磁盘至少 40GB、内存至少 4GB；
- 用户确认 staging 域名、数据库密码、JWT/Session 密钥、备份位置。

**Files:**
- Verify/modify: `server/Dockerfile`
- Create: `passenger-h5/Dockerfile`
- Create: `admin-web/Dockerfile`
- Create: `deploy/docker-compose.staging.yml`
- Create: `deploy/.env.staging.example`
- Create: `deploy/nginx/ride-dispatch.conf.template`
- Create: `deploy/README.md`
- Modify: `docs/07-ops/deployment-and-operations-v1.0.md`
- Modify: `docs/10-production/production-readiness-checklist-v1.0.md`

**Step 1: 固定生产配置边界**

后端使用 Java 21 运行，production/staging 不启用 `local` profile、不创建演示账号、不启用 H2 console、不允许明文 API。所有密码和密钥只放服务器受限 `.env`/Secret，不进入 Git。

**Step 2: 容器化四个运行单元**

Compose 运行 MySQL、Spring API、Passenger H5、Admin Web；统一 Docker network；数据库只暴露给内部网络，API 通过反向代理暴露。

**Step 3: 配置 Nginx 和 HTTPS**

为 Passenger H5、Admin Web、`/api` 和健康检查写路由；（旧计划中的高德安全代理已被当前无商业地图架构移除。）先用 staging 域名验证 DNS，再申请 HTTPS。证书续期必须有明确命令和日志。

**Step 4: 配置数据库迁移与种子边界**

启动顺序为 MySQL healthy → Flyway → API → Web；生产禁止 `LocalDevSeeder`。首次部署只执行迁移，不导入开发订单。

**Step 5: 做部署前静态检查**

Run: `docker compose -f deploy/docker-compose.staging.yml config`

Run: `pnpm --dir passenger-h5 install --frozen-lockfile && pnpm --dir passenger-h5 build`

Run: `pnpm --dir admin-web install --frozen-lockfile && pnpm --dir admin-web build`

Run: `mvn -B -ntp -f server/pom.xml verify`

Expected: Compose 配置无未替换变量，三个模块构建通过，镜像不携带开发密钥。

---

### Task 7: 执行 staging 部署、备份和恢复演练

**Files:**
- Create: `deploy/scripts/backup-mysql.sh`
- Create: `deploy/scripts/restore-mysql.sh`
- Create: `deploy/scripts/healthcheck.sh`
- Modify: `deploy/README.md`
- Modify: `docs/07-ops/deployment-and-operations-v1.0.md`

**Step 1: 配置防火墙和最小暴露面**

只开放 SSH、HTTP、HTTPS；MySQL 不开放公网；SSH 使用密钥，禁止把密码写入命令历史。

**Step 2: 部署并检查健康状态**

Run: `docker compose -f deploy/docker-compose.staging.yml --env-file .env.staging up -d`

Run: `./deploy/scripts/healthcheck.sh`

Expected: API health、Passenger 首页、Admin 登录页和数据库迁移状态全部成功。

**Step 3: 执行线上 smoke test**

用 staging URL 跑 Passenger H5 下单 → Admin 派单 → Emulator Driver App 登录；不使用开发机 `10.0.2.2` 地址，APK 改用 HTTPS staging API。

**Step 4: 做备份与恢复**

备份至少包含数据库和部署配置模板，备份副本放到应用服务器之外；在独立数据库容器恢复一份，核对订单、派单、履约事件和审计日志数量。

**Step 5: 记录回滚边界**

应用镜像可以回滚，数据库迁移必须按向前兼容策略处理；不得用回滚镜像删除已产生的订单/资金事实。

---

### Task 8: 完成 staging 版 Android 验收

**Files:**
- Modify: `driver-app/README.md`
- Create: `docs/08-roadmap/phase3-staging-report.md`

**Step 1: 构建 staging API APK**

Run: `./driver-app/gradlew -p driver-app assembleDebug -PdriverApiBaseUrl=https://staging.example.com`

Debug 仅用于联调；正式 release 必须使用签名密钥、关闭 cleartext，不把密钥放入仓库。

**Step 2: 重跑完整司机链路**

H5 下单 → Admin 派单 → Driver 登录/提醒 → 接受 → 到达上车点 → 乘客上车 → 行程中 → 到达目的地 → 录入金额。

**Step 3: 验证异常场景**

覆盖重复点击、网络中断后刷新、Token 失效、司机拒绝、派单被改派、定位过期；每项记录服务端最终状态和客户端提示。

**Step 4: 真实设备专项**

有 Android 真机后验证后台、锁屏、通知权限、省电策略和弱网；没有真机时只记录 Emulator 结果，不宣称真实设备 Gate 通过。

---

### Task 9: 在实现支付前完成 P0 业务决策

**Files:**
- Modify: `docs/09-decisions/PRD-V1.4-change-proposals.md`
- After approval modify: `docs/01-product/PRD-V1.3-baseline.md` or a formally versioned PRD V1.4
- After approval modify: `docs/03-domain/domain-rules-v1.0.md`
- After approval modify: `docs/04-data/data-model-v1.0.md`
- After approval modify: `docs/05-api/api-contract-v1.0.md`
- After approval modify: `docs/06-testing/acceptance-and-test-strategy-v1.0.md`

**Step 1: 确认收款主体和司机关系**

明确谁向乘客收款、是否允许线下收款、平台收入和司机收入如何定义、司机是否可提现。

**Step 2: 确认支付状态机**

明确支付尝试、失败/取消/超时、重复回调、主动查询、退款、金额锁定和异常人工处理。

**Step 3: 确认结算和提现规则**

明确可用余额/冻结余额、提现并发、驳回解冻、人工打款核销、账单和对账责任。

**Step 4: 将决策同步到所有契约**

只有 PRD、领域、数据、API、测试五类文档一致后，才允许进入 Payment/Ledger/Withdrawal 开发；未确认 Proposal 不得被代码静默升级。

---

### Task 10: 决策确认后实现 Mock 支付与资金领域

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/settlement/`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/withdrawal/`
- Create: `server/src/main/resources/db/migration/V005__payment_and_settlement.sql`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/payment/`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/settlement/`
- Create/modify: `passenger-h5/src/views/PaymentView.vue` and payment API files after契约确认
- Modify: `passenger-h5/src/views/OrderStatusView.vue`

**Step 1: 先写失败测试**

覆盖重复成功回调、不同支付尝试只能结算一次、金额不一致进入异常、付款页面关闭后服务端仍可确认。

**Step 2: 实现 MockPaymentProvider**

第三方流水号、业务 reference、金额和成功事件具备唯一约束；成功回调幂等，不能重复生成账本。

**Step 3: 实现 Ledger/Withdrawal 状态机**

余额变化只通过追加 Ledger；提现申请冻结可用余额，驳回解冻，已打款核销；每个状态变化产生审计记录。

**Step 4: 做恢复和并发测试**

验证两次提现并发、重复回调 10 次、数据库备份恢复后的余额核对；全部通过后才评估真实微信/支付宝 Provider。

---

## 最终完成标准

1. 新机器可以按文档启动本地后端、Passenger H5、Admin Web，并重复跑 Phase 2 Gate。
2. Driver App 的构建、单测、模拟器链路和错误恢复都有可追踪结果。
3. staging 云服务器使用 MySQL、HTTPS、受限密钥和可验证备份；不依赖开发机进程。
4. 有真机前不宣称后台/锁屏/厂商 Push 通过；有服务器后先做 staging，不直接上生产。
5. 支付、账本、提现只在 P0 业务决策落档后实现，且先 Mock、后沙箱、最后真实渠道。
6. 每个阶段结束时更新 `README.md`、路线图、测试策略和执行日志，保留失败证据和回归命令。

## 本计划不做的事情

- 不杀用户已有的 8080 Python 服务。
- 不把 H2/local profile 部署到生产。
- 不把 `D101 / driver123` 等演示账号带入服务器。
- 不在没有商户主体、资金关系和幂等规则确认前接微信/支付宝或提现。
- 不用截图或手工点通替代自动化测试和服务端状态核验。
