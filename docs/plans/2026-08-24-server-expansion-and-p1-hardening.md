# 服务器深度回归与 P1 可靠性 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在已部署的公网服务器上完成首期之外可验证功能的真实 API 回归，并补齐当前仍可独立开发的 P1 可靠性、支付 Provider 前置模型和运维硬化缺口。

**Architecture:** 以服务器上的 MySQL 数据和 Spring Boot 服务作为真实部署闭环，使用带运行批次的自动化 HTTP/SSE 测试验证权限、订单状态机、支付/结算/异常/提现一致性。真实微信/支付宝、银行打款、HTTPS 证书、厂商 Push 和真机能力继续作为外部验收项，不在本计划中伪造完成。

**Tech Stack:** Java 21/17 target、Spring Boot 4、MySQL 8、Nginx、Node.js fetch、SSE、Vue 3、Kotlin Compose、Gradle。

---

## Task 1: 参数化服务器回归测试入口

**Files:**
- Modify: `e2e/local-http-depth.cjs`
- Create: `e2e/deployed-http-smoke.cjs`
- Modify: `e2e/README.md`

**Steps:**
1. 让管理员、司机、API 基址从环境变量读取，本地默认值保持不变。
2. 新增部署冒烟脚本，覆盖健康代理、品牌读取、管理员/司机登录、权限边界、司机状态和管理端司机列表；输出不包含令牌和密码。
3. 使用 `DEPLOYED_API_URL=http://203.0.113.10` 作为文档示例；实际执行时必须由环境变量传入验收环境地址。
4. 失败时先定位网络/服务/数据层，再决定是否修改业务代码。

## Task 2: 公网 API 全链路回归

**Files:**
- Modify: `e2e/deployed-http-depth.cjs`
- Create: `docs/06-testing/2026-08-24-deployed-http-regression-report.md`

**Steps:**
1. 使用批次前缀创建公共订单，验证幂等重放、越权访问和接单前取消。
2. 验证后台派单、司机接受/拒绝、四阶段履约、金额元/分边界和订单完成。
3. 验证 Mock 支付失败重试、金额不一致、重复回调、收入入账、提现冻结/审核/人工打款和线下收款。
4. 验证支付异常登记、解决/驳回、累计金额限制、审计和管理查询。
5. 记录生产数据库实际 Flyway 版本、服务版本、测试批次和通过/失败清单；不删除业务数据。

## Task 3: SSE 与恢复专项

**Files:**
- Modify: `server/src/test/java/com/funccrypto/ridedispatch/realtime/`
- Modify: `driver-app/app/src/test/`
- Create: `e2e/deployed-sse-smoke.cjs`

**Steps:**
1. 用真实司机令牌连接验收环境 `/api/v1/driver/events`，断言 `CONNECTED` 事件和非司机拒绝。
2. 在连接期间触发派单/状态提交，断言事件的司机边界、事件类型和关联 attemptId/orderNo。
3. 模拟客户端断线、重连和重复事件，验证最终状态拉取与去重。
4. 更新 Android 单测并在 Emulator 上完成服务器地址启动与事件消费冒烟；锁屏/厂商 Push 仍标记为未验收。

## Task 4: 生产部署硬化

**Files:**
- Modify: `server/src/main/resources/application.yml`
- Modify: `deploy/systemd/ride-dispatch.service`
- Modify: `deploy/nginx/ride-dispatch.conf.template`
- Modify: `deploy/README.md`

**Steps:**
1. 增加必需生产环境变量校验，避免默认数据库密码或引导账号被误用于生产。
2. 保持后端 8080 回环监听，确认上传目录、日志和临时目录权限最小化。
3. 增加敏感字段日志脱敏、健康检查和备份/恢复演练说明。
4. 记录管理端非标准端口可能受网络限制；长期方案优先采用域名 HTTPS 443，临时方案可保留 SSH 隧道。

## Task 5: 支付 Provider 与对账前置模型

**Files:**
- Create/Modify: `server/src/main/java/com/funccrypto/ridedispatch/payment/provider/`
- Create/Modify: `server/src/main/java/com/funccrypto/ridedispatch/payment/reconciliation/`
- Test: `server/src/test/java/com/funccrypto/ridedispatch/payment/`

**Steps:**
1. 定义支付下单、回调验签、主动查询、退款查询和差异单接口，不接入真实商户密钥。
2. 保持 Mock Provider 为 local profile 默认实现，生产 profile 未配置 Provider 时启动失败或明确禁用支付写操作。
3. 为金额、订单号、第三方流水号和幂等键建立一致性校验。
4. 增加 Provider 成功/失败、重复回调、签名错误、查询差异和人工处理状态测试。

## Task 6: 完整验收与交付

**Steps:**
1. Maven 测试、部署 HTTP 回归、SSE 回归、Admin/H5 构建、Android 单测/Debug 构建全部通过。
2. 更新功能状态基线和部署回归报告，明确已验证、待外部验证、首期不做三类边界。
3. 重新构建指向验收 API 的 APK，记录 SHA256 和构建参数。
4. 只在没有失败项或明确记录阻塞项时汇报完成，不把 Mock、模拟器或 SSH 隧道描述成正式生产能力。

## 执行记录（2026-08-24）

> 历史环境记录仅代表当日，不代表当前 Android Gate。当前 Android compile/jvmTarget/Gate 使用 Temurin JDK17，当前后端 Gate 使用 Java 21；最新基线以当前 CI 和验收策略为准。

- [x] 服务器冒烟回归：`smoke:deployed` 7/7。
- [x] 服务器核心链路：`core:deployed` 7/7；生产 Mock Provider 边界明确为 404，线下收款路径通过。
- [x] 服务器 SSE 业务事件：`sse:deployed` 3/3。
- [x] 未注册路由统一返回 `NOT_FOUND`，新增后端回归测试并已部署。
- [x] Maven `verify`、Admin Web、Passenger H5、Android 构建均通过。
- [ ] 真实支付宝/微信 Provider、签名验签、异步回调、对账、银行打款：等待商户/金融机构参数，不能用本地 Mock 代替。
- [ ] HTTPS 域名、标准 443 管理端、真机/厂商 Push/后台定位：需要外部环境或运维资源。
