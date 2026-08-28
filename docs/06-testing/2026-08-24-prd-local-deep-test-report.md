# PRD 本地深度验收报告（2026-08-24）

## 范围

本轮验证本地服务、局域网页面、Android Emulator，以及已部署云服务器的本机反代健康状态；真实 Android 手机、真实支付商户和生产环境财务链路不在范围内。

## 已实际通过

| 范围 | 证据 |
| --- | --- |
| 后端单元/集成/API 流程 | `mvn verify`：36 tests，0 failures（含未注册路由结构化 404 回归） |
| 幂等、越权、取消、派单、接单、履约、金额和结算校验 | `e2e/local-http-depth.cjs`：36/36 PASS |
| Mock 支付完整链路 | 失败重试、金额不一致拒绝、重复成功回调幂等、订单完成和线上收入入账均 PASS |
| 线下收款完整链路 | 司机二次确认、订单完成、业务收入入账、可提现余额不增加均 PASS |
| 提现完整链路 | 冻结、超额拒绝、财务批准/打款、冻结余额核销和账本逐笔入账均 PASS |
| 人工退款异常链路 | 调度员越权拒绝、幂等重放、累计金额上限、登记查询、解决/驳回、已处理状态冲突均 PASS；支付仍为 `PAID`，司机账本不被自动改动 |
| 并发取消 vs 接受、并发派单、改派责任交接 | `DispatchServiceIntegrationTest`：11 tests PASS |
| 位置 5 分钟时效、10 km 范围和人数过滤 | `NearbyDriverServiceIntegrationTest` PASS |
| 公共 H5、司机定向 H5、Admin 基础调度流程 | `e2e/phase2-passenger-admin.cjs` PASS |
| 乘客付款浏览器流程 | `e2e/payment-settlement-browser.cjs` PASS；订单页 → 去付款 → ¥1200.00 → Mock 成功 → 已支付/已完成；Admin 退款异常页面可见 |
| Passenger H5 构建 | `CI=true pnpm --dir passenger-h5 build` PASS（vue-tsc + Vite） |
| Admin Web 构建 | `CI=true pnpm --dir admin-web build` PASS（vue-tsc + Vite） |
| Admin 支付/提现报表导出 | 浏览器回归验证 CSV 下载、元金额格式、中文 BOM 和收款账号脱敏 PASS |
| Android 构建与单元测试 | `:app:testDebugUnitTest :app:assembleDebug` PASS；APK 已安装到 `emulator-5554` |
| Android 本地新派单通知 | APK 安装后 Android 13 通知权限弹窗正常出现并可授权；定位前台服务已接入 SSE、断线指数重连和 15 秒轮询降级，并按 attemptId 去重通知 |
| 司机二维码能力 | 服务端返回 PNG `imageDataUrl`；Admin 支持预览/下载，Android 支持预览；QR 集成测试 PASS |
| Logo 上传与司机详情 | Admin Logo multipart 上传和受控读取、司机详情运营/结算快照已实现；对应后端测试和 Admin 构建 PASS |
| 实时事件接口 | 司机 Bearer Token 访问 `/api/v1/driver/events` 返回 `CONNECTED` SSE 事件；非司机/无令牌仍受鉴权保护 |
| MySQL 备份恢复 | PowerShell 备份/恢复脚本完成；临时恢复库验证 Flyway v001-v010 共 10 个版本可恢复 |
| Admin 订单、司机、品牌、代客建单页面 | 浏览器路由专项 PASS |
| Admin 强制取消 UI + 审计记录 | UI PASS；订单为 `CANCELLED`，有 `ORDER_FORCE_CANCELLED` |
| Admin 代客建单 UI | UI PASS；订单为 `ADMIN_CREATED/PENDING_DISPATCH` |
| 乘客接单前取消 UI | UI PASS；页面显示“订单已取消” |
| Android 冷启动登录 | 清除应用数据后登录 D101 PASS |
| Android 接受派单、拒绝派单 | 接受后订单进入活动列表；拒绝后回到 `PENDING_DISPATCH` 且有原因 |
| Android 四阶段履约和最终金额 | `ARRIVED_PICKUP → PASSENGER_ONBOARD → IN_TRANSIT → ARRIVED_DESTINATION → PENDING_PAYMENT` PASS |
| Android 金额单位边界 | 司机端输入/展示元；输入 `1200` 后界面显示 `¥1200.00`，服务端保存 `120000` 分 |
| Android 待收款展示 | 已提交金额后不再显示金额输入框，显示待收款卡片 |
| Android 定位服务 | 上线启动、下线停止、重启恢复、模拟 GPS 上传到后台 PASS |
| 局域网入口 | `192.168.31.244:8081/5173/5174` 和 Vite API 代理均 HTTP 200 |
| 云服务器本机部署 | MySQL、`ride-dispatch`、Nginx 均 active；后端 `/actuator/health` 为 UP；乘客端/管理端本机反代 200；后端仅监听 `127.0.0.1:8080` |
| 云服务器公网回归 | `smoke:deployed` 7/7 PASS；`core:deployed` 7/7 PASS；订单、履约、金额、支付上下文、生产支付边界、线下收款、收入保护和支付异常均验证，详见 [部署回归报告](./2026-08-24-deployed-http-regression-report.md) |
| 公网 APK 构建 | `app-debug.apk` 已用 `-PdriverApiBaseUrl=http://8.138.144.54` 构建，安装到 Pixel 7 Emulator 并启动 `MainActivity`；启动后无 `AndroidRuntime` 崩溃 |

## 已实现但仍需真实环境补测

- Android 锁屏、后台、系统省电策略下的新单通知。
- Push 通道断开、恢复和厂商后台限制。
- 真机定位功耗、权限拒绝、后台定位限制。
- 2G/高延迟、请求发出后断网、服务端成功但客户端超时后的重试恢复。
- 高德真实 Key 下的地图加载、搜索、定位和权限流程。
- 另一台局域网设备实际访问（当前只验证本机通过 Wi-Fi 地址访问）。
- 公网 80 已从开发机访问成功；当前 Wi-Fi/代理会重置非标准端口 8088，换热点或 SSH 隧道可访问管理端。安全组规则已确认，长期建议域名 HTTPS 443。

## 当前代码尚未实现，不能在本地闭环验证

- 真实微信/支付宝商户下单、签名验签、异步回调和对账（当前仅实现 local profile Mock 通道）。
- 真实银行/第三方钱包打款和财务机构回单（当前为本地审核/打款状态机）。
- 真实外部退款执行、退款结果通知和生产财务闭环（本地人工退款异常登记/解决/驳回已实现）。
- 生产 HTTPS、密钥管理、告警和灰度发布。

## 非阻塞警告

- Android SDK 命令行工具提示 SDK XML 版本不一致。
- H2 2.4 高于当前 Flyway 已验证版本；本地测试仍全部通过。
- Admin 生产包约 1 MB，Vite 给出分包优化提示。
- 本地开发模式仍会输出 Spring Security 临时密码和 Springdoc 开发端点警告。
