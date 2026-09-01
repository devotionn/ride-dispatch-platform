# Android Driver Verification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore a reproducible Android toolchain and validate the Driver App against a controlled test driver without depending on the unfiled production domain.

**Architecture:** Keep the repository's Java 17/Gradle 8.9 baseline. Use a stable local Temurin 17 installation and an environment-check script, then run unit tests and an emulator smoke flow against a local API endpoint. Use a dedicated server-side test driver only for API verification; production IP HTTP and self-signed TLS are not release targets.

**Tech Stack:** Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, Temurin JDK 17, Android SDK 35, ADB/emulator, Spring Boot API.

---

### Task 1: Restore the Android build toolchain

**Files:**
- Create: `driver-app/scripts/android-env.ps1`
- Modify: `driver-app/README.md`

**Steps:**
1. Detect a stable Temurin JDK 17 path and fail clearly when `java -version` is not 17.
2. Set `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` only for the current PowerShell process.
3. Run `:app:testDebugUnitTest :app:assembleDebug` with `-PdriverApiBaseUrl=http://10.0.2.2:8080`.

### Task 2: Recover emulator verification

**Files:** None unless the existing AVD is missing.

**Steps:**
1. Set `ANDROID_AVD_HOME` to the user's Android AVD directory.
2. Restart ADB and the Pixel 7 emulator with software rendering if it is offline.
3. Install the debug APK and verify the process starts without an Android runtime crash.

### Task 3: Verify a controlled driver API account

**Files:** Server-side database only; do not modify production driver `D101`.

**Steps:**
1. Create or identify a dedicated test driver with a known password through the admin API/database procedure.
2. Verify login, `/api/v1/driver/me/state`, and the SSE endpoint.
3. Remove or disable the temporary account after verification if it is not needed.

### Task 4: Document release gates

**Steps:**
1. Record that release builds must use a trusted HTTPS domain, not the IP or public HTTP.
2. Record ICP filing and valid certificate as external gates for real-device public testing.
3. Keep the temporary public admin HTTP configuration marked for rollback after filing.
