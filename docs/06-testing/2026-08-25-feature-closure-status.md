# 功能闭环状态基线与执行清单（2026-08-25）

## 1. 文档目的

本文档是当前仓库、测试环境和公网服务器的统一状态基线。它把功能分为：

1. 已实现且有自动化/部署证据；
2. 已实现但仍有外部环境缺口；
3. 尚未完全闭环、当前仍可开发；
4. PRD 首期明确不做。

后续开发、测试和部署只以本文档中的条目为准；每个条目必须补充代码、测试、部署证据或明确阻塞原因，不能把 Mock、模拟器或 SSH 隧道描述为生产能力。

## 2. 当前环境与证据

| 项目 | 当前状态 | 证据 |
| --- | --- | --- |
| 后端生产服务 | 已部署，Spring Boot 生产 profile，回环监听 127.0.0.1:8080 | `systemctl ride-dispatch`、Actuator health `UP` |
| 数据库 | MySQL 8，Flyway v010 | 服务器启动日志与迁移记录 |
| Web 入口 | 乘客端 80、管理端 8088 | Nginx 本机 200；公网 80 可访问 |
| 公网冒烟 | 通过 | `pnpm run smoke:deployed`：7/7 |
| 公网核心链路 | 通过 | `pnpm run core:deployed`：7/7 |
| 公网 SSE 业务事件 | 通过 | `pnpm run sse:deployed`：3/3 |
| 后端全量测试 | 通过 | Maven `verify`：36 tests，0 failures |
| Admin / Passenger Web | 通过 | `vue-tsc + vite build` |
| Android Debug APK | 通过 | JDK21 + SDK35：`testDebugUnitTest assembleDebug` |

详细公网证据见 [部署回归报告](./2026-08-24-deployed-http-regression-report.md)。

## 3. 已完成并已验证

### 3.1 订单、调度和履约

- 公共 H5、司机定向 H5、Admin 代客建单。
- 乘客 Token、接单前取消、幂等下单和越权保护。
- 附近司机 10km、位置 5 分钟时效、人数过滤和距离排序。
- 人工派单、司机接受/拒绝、改派、强制改派、强制取消、异常标记。
- `ARRIVED_PICKUP → PASSENGER_ONBOARD → IN_TRANSIT → ARRIVED_DESTINATION` 四阶段履约。
- 最终金额以分保存、Web/Android 以元展示；`1200 元 = 120000 分`。

### 3.2 支付、账务和异常

- local profile Mock 微信/支付宝：支付尝试、失败、金额校验、重复回调幂等。
- 线下收款二次确认、业务收入入账和可提现余额保护。
- 司机账户、账本、提现冻结、审核/驳回/人工打款。
- 支付异常登记、解决/驳回、累计金额限制和审计。
- 支付尝试、提现、支付异常的幂等键与冲突校验。
- Admin 支付、提现、支付异常列表和 CSV 导出；金额与账号脱敏规则已回归。

### 3.3 三端能力

- Admin：品牌、司机、订单、调度、支付、提现、异常、操作日志。
- Passenger H5：下单、状态、取消、付款页、Mock 支付二维码和元金额展示。
- Driver Android：登录、工作状态、位置、派单提醒、接受/拒绝、履约、金额、线下收款、收入、提现、历史订单和资料。
- 服务端司机 SSE、提交后发布事件、Android 指数退避重连和轮询降级。
- 司机二维码 PNG 生成/预览/下载；平台 Logo 上传与受控读取。
- MySQL 备份/恢复脚本和 Flyway v001-v010 恢复演练。

## 4. 已实现但未完全闭环

| 功能 | 已完成部分 | 未闭环原因 | 当前可执行动作 |
| --- | --- | --- | --- |
| 支付 Provider | Payment/Attempt/Mock 状态机和支付 Token 已完成 | 没有真实支付宝/微信商户参数、签名证书和回调地址 | 建立 Provider 接口、回调验签边界、主动查询/对账差异模型；保持生产 Mock 禁用 |
| 提现打款 | 本地人工审核、冻结、打款确认已完成 | 没有真实银行/钱包渠道 | 完善 provider boundary、状态机与对账模型；不伪造真实打款 |
| 生产 HTTPS/管理端标准入口 | Nginx 80/8088 已部署 | 无域名/证书；当前 Wi-Fi 对 8088 重置 | 准备 `/admin/` 或 HTTPS 443 方案，绑定域名后验收 |
| Android 后台通知 | SSE、轮询、通知权限和模拟器冷启动已验证 | 无真机、厂商 Push、锁屏/省电数据 | 继续 Android 单测、事件去重和恢复测试；真机条件具备后专项验收 |
| 弱网恢复 | GET 重试、SSE 重连和服务端幂等已实现 | 无真实 2G/断网/系统杀进程环境 | 扩展可重复的 HTTP/SSE 故障注入测试 |
| 生产硬化 | 回环后端、备份、文件头/大小校验、结构化错误已完成 | 限流、密钥轮换、告警、灰度还未落地 | 先补配置校验和部署自检，再做真实运维验收 |

## 5. 当前批次：支付 Provider/对账前置模型

本批次不接入真实商户，不改变生产默认行为，目标是把后续真实接入所需的边界固定下来：

1. 定义下单、回调验签、主动查询和退款查询的 Provider 契约；
2. 定义第三方流水号、金额、商户单号、幂等键的一致性校验；
3. 定义支付状态与对账差异状态，不直接改动现有支付结算事务；
4. 为成功、失败、重复回调、金额不符、签名错误、查询差异补齐单元测试；
5. 生产 profile 仍不暴露 local Mock 回调，部署回归继续验证 `404 NOT_FOUND` 边界。

本批次 Provider/对账模型已落地为纯 Java 边界；当前没有真实渠道实现，因此注册表在生产为空时返回 `PAYMENT_PROVIDER_UNAVAILABLE`，不影响已有 local Mock 控制器和线下收款。

## 6. 交付定义

本批次只有同时满足以下条件才算完成：

- Provider 契约和对账模型有明确 Java 类型及文档；
- 所有校验路径有自动化测试；
- local Mock 现有 36 个后端测试不回归；
- Admin/H5/Android 构建通过；
- Java17 生产 JAR 部署并健康检查通过；
- 公网 smoke、core、SSE 回归通过；
- 本文档补充实际结果和未闭环边界。

## 7. PRD 首期明确不做

微信公众号体系、乘客原生 App、短信验证码/会员、自动/AI 派单、导航/实时轨迹、自动计价、IM/电话、自动退款/自动代付、优惠券/积分/评价/发票等继续保持不做，不纳入本批次。

## 8. 执行记录

### 2026-08-25 Provider/对账批次

- 变更：`payment/provider` 契约、值对象、注册表；`payment/reconciliation` 状态、快照、差异比较器；API 契约补充 Provider/对账边界。
- TDD 聚焦测试：11/11 PASS；包含参数校验、重复 Provider、空注册表、金额/流水/状态差异。
- 后端全量：Maven `verify` 47 tests，0 failures。
- Web：Admin Web、Passenger H5 `vue-tsc + vite build` PASS。
- Android：JDK21 + SDK35，`testDebugUnitTest assembleDebug` PASS。
- 部署：Java17 target JAR 上传并重启成功；Actuator `UP`，Nginx 80/8088 本机 200。
- 公网回归：`smoke:deployed` 7/7、`core:deployed` 7/7、`sse:deployed` 3/3 PASS。
- 未闭环：真实支付宝/微信 Provider、签名证书、异步回调、主动查询、对账补偿和银行打款仍等待外部参数；本批次没有伪造这些能力。
