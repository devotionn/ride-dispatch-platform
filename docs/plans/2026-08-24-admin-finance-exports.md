# Admin Finance Export Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add safe, human-readable CSV exports for the existing payment and withdrawal operations pages without expanding backend scope.

**Architecture:** Reuse the already-authorized payment/withdrawal list APIs and export the currently loaded rows in the browser. A shared CSV utility will add a UTF-8 BOM, escape commas/quotes/newlines, format fen as yuan, and mask sensitive withdrawal account values. No new persistence or API endpoints are needed.

**Tech Stack:** Vue 3, TypeScript, Element Plus, browser Blob/URL APIs, Vite build.

---

### Task 1: Define export contract and utility

**Files:**
- Create: `admin-web/src/utils/csv.ts`
- Test: `admin-web` type-check/build gate

**Steps:**
1. Define a `CsvColumn<T>` descriptor and `downloadCsv<T>(filename, rows, columns)` helper.
2. Escape quotes, commas, CR/LF and prepend UTF-8 BOM so Chinese opens correctly in Excel.
3. Keep formatting in column descriptors so payment and withdrawal pages do not duplicate CSV mechanics.
4. Run `CI=true pnpm --dir admin-web build`; expected: PASS.

### Task 2: Add payment export

**Files:**
- Modify: `admin-web/src/views/PaymentsView.vue`

**Steps:**
1. Add an “导出 CSV” button beside refresh, disabled while loading or when no rows exist.
2. Export payment number, yuan amount, status, settlement method, attempt count, latest channel/status/transaction and created time.
3. Keep raw payment access tokens out of the export.
4. Run the Admin build; expected: PASS.

### Task 3: Add withdrawal export

**Files:**
- Modify: `admin-web/src/views/WithdrawalsView.vue`

**Steps:**
1. Add an “导出 CSV” button beside refresh.
2. Export withdrawal number, yuan amount, channel, masked account, status, reason, created/reviewed/paid times.
3. Mask account values in the export (retain last four characters only) to avoid creating a new sensitive-data leak.
4. Run the Admin build; expected: PASS.

### Task 4: Verify and document

**Files:**
- Modify: `e2e/README.md`
- Modify: `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`

**Steps:**
1. Add the export behavior to the local acceptance checklist.
2. Run `git diff --check`.
3. Run `CI=true pnpm --dir admin-web build` and the existing Phase 2 browser gate.
4. Record the export scope and the fact that exports contain only currently loaded, authorized rows.

