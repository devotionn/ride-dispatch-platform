# 功能完成基线与后续开发 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 根据功能状态基线，补齐所有当前无需云服务器、真实支付商户或 Android 真机即可完成的 PRD 缺口，并用自动化测试验证每项交付。

**Architecture:** 继续使用 Spring Boot 模块化单体作为业务真相；文件资源采用受控本地存储并通过后端 URL 暴露，后续可替换为对象存储。二维码在服务端生成统一 PNG/SVG 数据，Admin 和 Android 复用同一业务 URL。实时通知采用事件模型 + 可选 SSE，客户端保留轮询降级，不改变订单状态机。

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Flyway, H2/MySQL, Vue 3 + Element Plus, Kotlin Compose, ZXing, pnpm, Gradle。

---

## Task 1: 建立并校正功能状态基线

**Files:**
- Create: `docs/06-testing/2026-08-24-feature-status-and-development-baseline.md`
- Create: `docs/plans/2026-08-24-feature-completion.md`
- Modify: `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`

**Steps:**
1. 固化已完成、可继续开发、外部依赖、首期不做四类状态。
2. 将历史测试数量从 28 更新为当前实际 35，并注明验证边界。
3. 检查文档链接和路径可读性。

## Task 2: Logo 文件上传与安全读取

**Files:**
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/brand/api/BrandController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/brand/PlatformBrandService.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/brand/BrandLogoStorage.java`
- Modify: `server/src/main/resources/application.yml`
- Modify: `admin-web/src/api/brand.ts`
- Modify: `admin-web/src/views/BrandView.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/brand/BrandApiIntegrationTest.java`

**Steps:**
1. 写上传接口失败测试：非图片、超过大小、未授权必须失败。
2. 实现 PNG/JPEG/WebP 校验、随机文件名、路径约束和公开读取接口。
3. 上传成功后更新品牌 URL 并写审计日志。
4. Admin 增加文件选择、预览、上传、替换和错误提示；保留 URL 配置兼容。
5. 运行品牌 API 测试和 Admin 构建。

## Task 3: 司机二维码图片预览与导出

**Files:**
- Modify: `server/pom.xml`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/DriverAdminService.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/api/DriverAdminController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/api/DriverSelfController.java`
- Modify: `admin-web/src/api/drivers.ts`
- Modify: `admin-web/src/views/DriversView.vue`
- Modify: `driver-app/app/build.gradle.kts`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/domain/Models.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/network/DriverApi.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/driver/DriverQrIntegrationTest.java`

**Steps:**
1. 写二维码响应和权限测试。
2. 用 ZXing 生成只包含司机定向下单 URL 的 PNG 数据。
3. Admin 增加二维码弹窗、下载和复制链接。
4. Android 增加二维码图像展示；付款二维码继续使用独立订单 Token。
5. 运行 Maven、Admin build、Android test/assembleDebug。

## Task 4: 司机详情统计

**Files:**
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/DriverAdminService.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/api/DriverAdminController.java`
- Modify: `admin-web/src/api/drivers.ts`
- Modify: `admin-web/src/views/DriversView.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/driver/DriverAdminServiceIntegrationTest.java`

**Steps:**
1. 写司机详情统计测试，覆盖当前订单、历史完成数、业务收入、可提现余额和提现列表。
2. 增加只读聚合查询，避免修改订单/账务事实表。
3. 增加后台司机详情抽屉和脱敏提现账号展示。
4. 运行后端测试和 Admin 构建。

## Task 5: 实时事件与轮询降级

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/realtime/...`
- Modify: order/dispatch services to publish committed events
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/location/LocationForegroundService.kt`
- Test: realtime integration tests and Android unit tests

**Steps:**
1. 定义不携带敏感信息的事件模型和司机/订单订阅边界。
2. 增加 SSE 本地通道，连接失败自动回退轮询。
3. 对事件去重、断线重连、服务端最终状态拉取写测试。
4. 运行后端和 Android 回归。

## Task 6: 弱网、部署和支付前置模型

**Files:**
- Modify: `deploy/docker-compose.dev.yml`
- Create: `deploy/scripts/backup-mysql.ps1`, `deploy/scripts/restore-mysql.ps1`
- Modify: server security/configuration and payment packages
- Create: provider contract/reconciliation model and tests

**Steps:**
1. 增加配置校验、限流、日志脱敏和关键健康检查。
2. 增加 MySQL 备份/恢复脚本并执行恢复演练。
3. 抽象支付 Provider、回调验签边界和差异单，不接真实商户。
4. 增加并发/断连/重复回调测试。
5. 执行完整构建、HTTP、浏览器和 Android 回归。

## Execution status

- Task 1 completed: feature status baseline and local verification boundaries are documented.
- Task 2 completed: logo upload/storage/read path and Admin upload UI are implemented and tested.
- Task 3 completed: driver QR PNG generation, Admin preview/download, and Android preview are implemented and tested.
- Task 4 completed: driver detail operational and settlement snapshots are implemented and tested.
- Task 5 implementation completed for the local environment: server-side SSE channel, after-commit dispatch/status events, Android SSE consumer, exponential reconnect, and polling fallback are implemented and verified with Android unit/build checks. A full emulator dispatch-to-event-to-notification regression and vendor push remain pending.
- Task 6 partially completed: MySQL backup/restore scripts and restore drill are complete; deployment hardening and payment provider/reconciliation preparation remain pending.

Verification gate for this execution: Maven `verify` passed with 35 tests and 0 failures; executable JAR packaging passed; local health check is UP; authenticated driver SSE connection returned `CONNECTED`.
