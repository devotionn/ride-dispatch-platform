# Payment Provider and Reconciliation Boundary Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不接入真实商户密钥的前提下，为支付下单、回调验签、主动查询和对账差异建立可测试的 Provider 边界，并保持现有 Mock/local 与生产禁用语义不变。

**Architecture:** 新增纯 Java Provider 契约和不可变请求/结果类型；Provider 只负责第三方协议边界，不直接修改 Payment/Order/Ledger。新增对账差异分类器，把本地支付快照与第三方快照比较为一致、金额不符、流水不符、状态不符或本地缺失；真实接入时由适配器调用现有 PaymentService 事务。

**Tech Stack:** Java 21 tests / Java 17 production target、Spring Boot 4、JUnit 5、现有 PaymentChannel/PaymentStatus/PaymentAttemptStatus。

---

### Task 1: 定义 Provider 契约和值对象

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentProvider.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentProviderRequest.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentProviderResult.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentCallback.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentQueryResult.java`

**Step 1: Write failing contract tests**

Add tests for required fields, positive fen amount, nonblank merchant order number, and callback transaction number.

**Step 2: Run the focused tests**

Run: `mvn -Dtest=PaymentProviderContractTest test`

Expected: FAIL until the value objects and validation exceptions exist.

**Step 3: Implement minimal immutable records and interface**

Use records with compact constructors. The interface must expose `PaymentChannel channel()`, `create`, `verifyCallback`, and `query` methods, without Spring or persistence dependencies.

**Step 4: Run focused tests**

Expected: PASS for all contract validation cases.

### Task 2: Add reconciliation diff model

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/reconciliation/ReconciliationStatus.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/reconciliation/PaymentReconciliationSnapshot.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/reconciliation/ReconciliationDiff.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/reconciliation/PaymentReconciliationComparator.java`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/payment/reconciliation/PaymentReconciliationComparatorTest.java`

**Step 1: Write failing comparison tests**

Cover exact match, local pending vs provider paid, amount mismatch, transaction mismatch, provider-only and local-only snapshots.

**Step 2: Implement deterministic comparison**

The comparator must never mutate payment entities, must return one status plus a safe reason code, and must compare fen amounts exactly.

**Step 3: Run focused tests**

Run: `mvn -Dtest=PaymentReconciliationComparatorTest test`

Expected: PASS with no database or network dependency.

### Task 3: Document and wire availability without changing settlement

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/PaymentProviderRegistry.java`
- Create: `server/src/test/java/com/funccrypto/ridedispatch/payment/provider/PaymentProviderRegistryTest.java`
- Modify: `docs/05-api/api-contract-v1.0.md`

**Step 1: Test registry behavior**

Assert duplicate channels fail, unknown channels return a clear business error, and no local Mock Provider is exposed unless the `local` profile is active.

**Step 2: Implement registry**

Use constructor-injected providers and an immutable channel map. Keep the registry internal for now; do not add a production payment callback endpoint.

**Step 3: Update API contract**

Document Provider boundary, callback amount/transaction/signature requirements, and production behavior when no real provider is configured.

**Step 4: Run focused and full backend tests**

Run: `mvn verify`

Expected: all tests pass and existing local Mock callback behavior remains unchanged.

### Task 4: Build, deploy, and run regression gates

**Files:**
- Modify: `docs/06-testing/2026-08-25-feature-closure-status.md`
- Modify: `docs/06-testing/2026-08-24-deployed-http-regression-report.md`

**Step 1: Build production JAR**

Run with Java 17 target: `mvn clean package -Dmaven.test.skip=true -Dmaven.compiler.release=17 -Djava.version=17 -B`.

**Step 2: Upload and restart**

Upload the JAR to `/opt/ride-dispatch/app/ride-dispatch-server.jar`, restart `ride-dispatch`, and verify Actuator health and Nginx 80/8088.

**Step 3: Run deployed gates**

Run `pnpm run smoke:deployed`, `pnpm run core:deployed`, and `pnpm run sse:deployed` with credentials supplied through environment variables only.

**Step 4: Record evidence**

Append exact test counts, deployment result, and remaining external blockers to the closure status and deployed regression documents.

## Execution result (2026-08-25)

> 历史环境记录：本节的 Android JDK21、Java17 target JAR 和测试数量仅代表 2026-08-25 当日，不代表当前 Android Gate。当前 Android compile/jvmTarget/Gate 使用 Temurin JDK17，当前后端 Gate 使用 Java 21；最新轻量定位基线见 [2026-08-30 验收策略](../06-testing/acceptance-and-test-strategy-v1.0.md)。

- Task 1-3 complete: provider contract, empty/duplicate-safe registry, reconciliation comparator, tests and API documentation.
- Focused tests: 11/11 PASS; full Maven verify: 47/47 PASS.
- Admin Web, Passenger H5, Android JDK21 + SDK35 build: PASS.
- Java17 target JAR deployed; Actuator and Nginx checks: PASS.
- Deployed smoke/core/SSE: 7/7, 7/7, 3/3 PASS.
- External blockers remain intentionally explicit: real merchant credentials, signatures, callbacks, reconciliation jobs and bank payout.
