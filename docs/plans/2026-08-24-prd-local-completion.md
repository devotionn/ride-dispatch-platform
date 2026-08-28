# PRD Local Completion Implementation Plan

> **For Codex:** Implement this plan task-by-task with test-first changes and a verification checkpoint after each batch.

**Goal:** Complete the PRD V1.3 capabilities that are locally implementable without real payment credentials, cloud infrastructure, or a physical Android device.

**Architecture:** Keep the Spring Boot modular monolith as the single business authority. Add missing administrative operations through transactional domain services and append-only ledger/audit records; expose focused APIs consumed by Admin Web, Passenger H5, and the Android driver app. Do not implement first-phase exclusions or freeze unconfirmed V1.4 business decisions into the model.

**Tech Stack:** Java 21, Spring Boot 4, JPA/Flyway, H2/MySQL modes, Vue 3 + Vite + Element Plus/Vant, Kotlin Compose Android, Node/Playwright E2E.

---

## Task 1: Complete driver administration lifecycle

**Files:**
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/DriverEntity.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/DriverAdminService.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/api/DriverAdminController.java`
- Modify: `admin-web/src/api/drivers.ts`
- Modify: `admin-web/src/views/DriversView.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/driver/DriverAdminServiceIntegrationTest.java`

Implement detail/edit/enable-disable/status transitions, vehicle updates, QR view/export data, and audit records. Preserve the existing `canReceiveNewOrder` guard for disabled accounts.

## Task 2: Complete admin order cancellation and exception handling

**Files:**
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/RideOrderEntity.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/OrderManagementService.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/api/AdminOrderController.java`
- Modify: `admin-web/src/api/orders.ts`
- Modify: `admin-web/src/views/OrdersView.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/dispatch/DispatchServiceIntegrationTest.java`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/api/Phase1HttpFlowIntegrationTest.java`

Add pending-order cancellation and explicit exception marking/resolution-safe transitions with required reasons and audit snapshots. Do not change the force-cancel semantics for accepted/in-service orders.

## Task 3: Add global operation-log query

**Files:**
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/audit/OperationLogRepository.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/audit/api/AdminOperationLogController.java`
- Create: `admin-web/src/api/operationLogs.ts`
- Create: `admin-web/src/views/OperationLogsView.vue`
- Modify: `admin-web/src/router.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/api/Phase1HttpFlowIntegrationTest.java`

Support paged filtering by object type, object id/order number, action, operator, and request id. Keep sensitive snapshots masked as currently stored.

## Task 4: Implement offline payment adjustment as an append-only financial correction

**Files:**
- Create: `server/src/main/resources/db/migration/V009__offline_payment_adjustment.sql`
- Create/modify: `server/src/main/java/com/funccrypto/ridedispatch/settlement/*`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/AdminOfflineAdjustmentController.java`
- Create: `admin-web/src/api/offlineAdjustments.ts`
- Modify: `admin-web/src/views/PaymentsView.vue`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/settlement/SettlementServiceIntegrationTest.java`

Never edit or delete the original offline ledger row. Record a compensating adjustment with before/after balances, reason, operator, and idempotency key; enforce that corrections cannot create a withdrawable balance for cash already collected.

## Task 5: Harden write idempotency and local finance role coverage

**Files:**
- Create: `server/src/main/resources/db/migration/V010__write_idempotency.sql`
- Modify: payment attempt and withdrawal entities/services/controllers
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/LocalDevSeeder.java`
- Modify: `e2e/local-http-depth.cjs`
- Test: payment/settlement integration tests

Add scoped idempotency for payment-attempt creation and withdrawal requests, reject same-key/different-payload conflicts, seed a local FINANCE account, and preserve state-conflict behavior for repeated approval/mark-paid calls.

## Task 6: Complete driver Android product surfaces

**Files:**
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/network/DriverApi.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/domain/Models.kt`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/driver/api/DriverSelfController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/order/api/DriverOrderController.java`
- Test: `driver-app/app/src/test/java/com/funccrypto/ridedispatch/driver/domain/DriverFlowTest.kt`

Expose and render platform brand, profile/vehicle, QR link, order history, ledger/withdrawal history, reject reason choices, and payment channel selection. Keep the current foreground-service polling as a local fallback and label external Push as pending.

## Task 7: Add local order-payment QR presentation

**Files:**
- Modify: `passenger-h5/package.json`
- Modify: `passenger-h5/src/views/PaymentView.vue`
- Modify: `passenger-h5/src/views/OrderStatusView.vue`
- Modify: `passenger-h5/src/styles.css`
- Test: `e2e/payment-settlement-browser.cjs`

Generate a QR image for the order-specific payment URL/token locally. Keep the real WeChat/Alipay provider QR and callback integration out of local scope.

## Task 8: MySQL/local deployment and regression gate

**Files:**
- Modify: `deploy/docker-compose.dev.yml`
- Modify: `deploy/scripts/*.ps1`
- Modify: `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`
- Modify: `docs/07-ops/deployment-and-operations-v1.0.md`
- Modify: `README.md`

Run Flyway and the HTTP smoke suite against MySQL when Docker is available, add backup/restore and readiness checks that are safe for local use, then run Maven, both web builds, Android tests/build, HTTP depth, browser E2E, and `git diff --check`.

