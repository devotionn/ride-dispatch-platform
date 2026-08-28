# Manual Refund Exception Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an auditable manual-refund exception workflow for paid payments without pretending to execute a real third-party refund.

**Architecture:** A new `payment_exception` aggregate references an existing Payment and order. Admin/Finance can open a full or partial refund exception, then resolve it with an external refund reference and note or reject it with a reason. The original Payment remains `PAID`; no driver balance or ledger is mutated automatically. Every transition is recorded through the existing `AuditService`.

**Tech Stack:** Spring Boot 4, JPA/Flyway, H2/MySQL-compatible SQL, Vue 3 + Element Plus.

---

### Task 1: Add exception persistence and service rules

**Files:**
- Create: `server/src/main/resources/db/migration/V007__payment_exception.sql`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentExceptionStatus.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentExceptionEntity.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentExceptionRepository.java`
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/PaymentExceptionService.java`

**Steps:**
1. Persist exception number, payment/order IDs, requested amount in fen, reason, status, external refund reference, resolution note, operators and timestamps.
2. Allow creation only for an existing `PAID` payment and amount `1..payment.amount`; all non-rejected exceptions for one payment may not cumulatively exceed the payment amount.
3. Require an `Idempotency-Key` and replay the existing exception on a repeated key.
4. Allow only `OPEN → RESOLVED` with nonblank external reference/note or `OPEN → REJECTED` with a reason.
5. Do not change Payment status, order status, driver account or ledger in any exception transition.
6. Log creation and resolution/rejection with `AuditService`.

### Task 2: Expose authorized Admin APIs

**Files:**
- Create: `server/src/main/java/com/funccrypto/ridedispatch/payment/api/AdminPaymentExceptionController.java`
- Modify: `server/src/main/java/com/funccrypto/ridedispatch/auth/SecurityConfig.java` only if route authorization needs adjustment

**Steps:**
1. Add `GET /api/v1/admin/payment-exceptions` for ADMIN/FINANCE.
2. Add `POST /api/v1/admin/payment-exceptions` with payment number, requested amount and reason.
3. Add resolve/reject endpoints with validation and authenticated operator IDs.
4. Return yuan-facing UI data as integer fen in the API, matching existing contracts.

### Task 3: Add Admin exception page

**Files:**
- Create: `admin-web/src/api/paymentExceptions.ts`
- Modify: `admin-web/src/domain/types.ts`
- Create: `admin-web/src/views/PaymentExceptionsView.vue`
- Modify: `admin-web/src/router.ts`
- Modify: `admin-web/src/layouts/AdminLayout.vue`

**Steps:**
1. Add list/create/resolve/reject client calls.
2. Add a table with amount in yuan, status, reason, operator notes and timestamps.
3. Use confirmation dialogs for create, resolve and reject; never expose or collect payment tokens.
4. Add a navigation entry labelled “退款异常”.

### Task 4: Test and document

**Files:**
- Modify: `e2e/local-http-depth.cjs`
- Modify: `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`
- Modify: `docs/01-product/PRD-V1.3-baseline.md`

**Steps:**
1. After a successful Mock payment, create a partial refund exception, resolve it, and assert Payment remains `PAID` and no extra settlement ledger is created.
2. Create a second exception and reject it; assert invalid state transitions are rejected.
3. Run Maven verify, HTTP depth, Admin build and browser gates.
4. Record that actual external refund execution remains outside local scope.
