# Driver App

Phase 3 Android 最小垂直切片，当前覆盖：

- 司机登录和本地会话保存；
- 读取/更新工作状态；
- 当前可接人数；
- Android 前台定位服务，只有服务端确认进入 `AVAILABLE` 后才启动；
- 待确认订单列表；
- 接受/拒绝派单；
- 活动订单和四段履约推进；
- 到达目的地后提交最终金额。

金额在司机界面以人民币“元”录入和展示，提交 API 时换算为整数“分”；例如输入 `1200` 元，服务端保存 `120000`。

## 本地运行

需要 Android Studio、Android SDK 35 和 JDK 17。项目 Android 编译选项和 Kotlin `jvmTarget` 固定为 Java 17；Android Gate 使用 Temurin JDK 17。仓库已包含锁定到 Gradle 8.9 并校验发行包摘要的 Gradle Wrapper；已验证 `testDebugUnitTest`、`assembleDebug` 和 `lintDebug`。Pixel 7 Android 15 Emulator 已可用软件渲染启动并完成 APK 冷启动验证；真实手机仍未验证。

为避免依赖会被系统清理的临时 JDK 目录，Windows 可直接运行仓库脚本。脚本会自动选择 `C:\Program Files\Eclipse Adoptium\jdk-17*`，并只在脚本进程内设置 Android 环境：

```powershell
.\driver-app\scripts\android-verify.ps1
.\driver-app\scripts\android-verify.ps1 -Install
```

后端 Java 21 与 Android Java 17 是两个独立基线；Windows 构建前请在当前 PowerShell 会话指定 Temurin JDK 17，并同时设置 Android SDK。JDK 25 暂不兼容项目使用的 Kotlin/Gradle 工具链（会在脚本初始化阶段报 `IllegalArgumentException: 25.0.4`）：

```powershell
$env:JAVA_HOME = 'C:\path\to\temurin-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

模拟器默认访问宿主机后端：

```powershell
.\driver-app\gradlew.bat -p driver-app :app:installDebug
```

默认 API 地址为 `http://10.0.2.2:8080`。真机联调时将后端局域网地址传入 Gradle：

```powershell
.\driver-app\gradlew.bat -p driver-app :app:installDebug -PdriverApiBaseUrl=http://192.168.1.20:8080
```

macOS/Linux 可从仓库根目录运行（即使脚本尚未设置执行位也可用）：

```bash
bash driver-app/gradlew -p driver-app :app:installDebug -PdriverApiBaseUrl=http://192.168.1.20:8080
```

后端本地种子司机：`D101 / driver123`、`D102 / driver123`。生产环境不得使用这些账号或明文 HTTP；`usesCleartextTraffic` 只为本地联调保留。

如果 Windows 环境变量 ANDROID_AVD_HOME 指向了不存在的目录，先在当前 PowerShell 会话修正 AVD 目录，再用软件渲染启动：

```powershell
$env:ANDROID_AVD_HOME = "$env:USERPROFILE\.android\avd"
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd Pixel_7 -gpu swiftshader_indirect -no-snapshot -no-boot-anim
```

服务器验收 APK 的示例构建命令如下；`203.0.113.10` 为文档保留地址，实际验收时替换为目标 API 域名或地址：

```powershell
.\driver-app\gradlew.bat -p driver-app :app:testDebugUnitTest :app:assembleDebug `
  -PdriverApiBaseUrl=http://203.0.113.10 --no-daemon --console=plain
```

## 当前限制

- 可接单状态启动定位前台服务后，会轮询待确认派单并发出本地系统通知；通知使用 attemptId 去重，点击通知回到司机工作台；
- Android 厂商省电策略、锁屏限制和弱网恢复仍需要真机专项验证，当前未接入 FCM 等外部 Push 服务；
- 支付、收入、账本、提现页面不在本切片内；
- 所有接受/拒绝/履约/金额动作都以服务端成功响应后刷新 UI，不在客户端乐观推进状态。
- 当前 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；包名 `com.funccrypto.ridedispatch.driver`，target SDK 35。
