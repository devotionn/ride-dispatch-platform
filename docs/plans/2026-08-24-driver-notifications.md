# Driver Notification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver local Android system notifications for newly assigned driver orders while the driver is in AVAILABLE status and the location foreground service is running.

**Architecture:** Reuse the existing location foreground service as the durable local polling process. It will poll the authenticated driver's pending-confirmation endpoint, persist a bounded set of notified attempt IDs for deduplication, and post high-importance notifications with a tap target back to `MainActivity`. The Compose screen requests Android 13 notification permission once; no external push provider is introduced.

**Tech Stack:** Kotlin, Android foreground service, AndroidX NotificationCompat, SharedPreferences, existing `DriverApi`.

---

### Task 1: Add notification channels and service polling

**Files:**
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/location/LocationForegroundService.kt`

**Steps:**
1. Create a high-importance order notification channel alongside the existing low-importance location channel.
2. Start one coroutine polling pending confirmations every 15 seconds while the service is alive.
3. Use `SessionStore` and `DriverApi.pendingConfirmations` so the service follows the currently logged-in driver.
4. Persist notified attempt IDs in service preferences, remove IDs no longer pending, and notify only new IDs.
5. Build a notification with route text, vibration/default sound, and a `MainActivity` PendingIntent.

### Task 2: Request runtime notification permission

**Files:**
- Modify: `driver-app/app/src/main/java/com/funccrypto/ridedispatch/driver/MainActivity.kt`

**Steps:**
1. Add an Android 13+ `POST_NOTIFICATIONS` permission launcher in the logged-in home screen.
2. Request it once when the driver home is shown; leave Android 12 and below unchanged.
3. Keep location permission and notification permission independent so a denied notification permission does not block driving/location behavior.

### Task 3: Verify build and emulator behavior

**Files:**
- Modify: `driver-app/README.md`
- Modify: `docs/06-testing/2026-08-24-prd-local-deep-test-report.md`

**Steps:**
1. Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug -PdriverApiBaseUrl=http://10.0.2.2:8081` with JDK 21.
2. Install the APK on `emulator-5554` and verify the driver home still renders and the notification permission request does not crash startup.
3. Record that background notification delivery is locally implemented but manufacturer-specific battery restrictions and real-device push delivery remain external gates.

