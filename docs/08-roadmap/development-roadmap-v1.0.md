# 开发路线图 V1.0

- 状态：BASELINE
- 策略：按真实业务闭环推进，而不是四个端同时铺大量页面。

## Phase 0 — 文档与领域基线

### 目标

在写业务代码前统一需求、领域、数据、API 和测试不变量。

### 交付

- PRD V1.3 Markdown 基线
- 技术实施方案
- 领域规则
- 数据模型草案
- API 契约草案
- 测试策略
- PRD V1.4 待确认项

### Gate

- 三种入口、订单主状态、派单/拒单/改派、支付、线下收款、提现语义不再互相矛盾。
- 关键未确认事项已经显式列出。

## Phase 1 — 工程骨架与 Backend Core

### 交付

- monorepo 目录
- Spring Boot 工程
- MySQL + Flyway
- 统一错误模型
- Admin/Driver 基础认证
- PlatformBrand
- Driver / Vehicle / Location
- Order
- DispatchAttempt
- OperationLog
- OpenAPI

### 必须打通

```text
公共订单创建
→ 待接单
→ 查询附近司机
→ 后台派单
→ 司机接受/拒绝
```

### Gate

- 状态与并发自动化测试 PASS。
- Flyway 可在空库完整建立 schema。

## Phase 2 — Passenger H5 + Admin 调度台

### Passenger

- 公共 H5
- 司机定向 H5
- 浏览器原生定位、Place Catalog 和手工地址输入（坐标可选）
- 乘客订单 Token
- 接单前取消
- 订单状态页

### Admin

- 登录和品牌
- 司机列表
- 订单中心
- 后台代客建单
- 附近司机
- 人工派单
- 待确认时长
- 拒绝原因
- 改派/强制操作框架
- 操作时间线

### Gate

真实浏览器完成：

```text
乘客下单
→ 后台出现
→ 人工派司机
```

**Gate 状态：PASS（2026-08-30）**

- 可重复脚本：`e2e/phase2-passenger-admin.cjs`；
- 公共 H5 纯文字地址下单，Admin 显示无坐标提示并完成人工派单；
- 公共 H5 浏览器原生定位上车点 + 手工目的地，Admin 验证坐标和距离；
- 司机定向入口 `/ride/d/QRD101` 创建订单后保持 `PENDING_DRIVER_CONFIRM`；
- Java 21 后端 `mvn clean verify`：55 个测试通过；Passenger H5、Admin Web 构建通过；
- 当前实现不依赖商业地图 SDK、地图 Key 或地图服务代理。

## Phase 3 — Android Driver

当前状态：Compose 司机工作台、司机 API、定位前台服务和本地新派单通知已接入；单元测试与 Debug APK 构建通过。Android 13 通知权限和模拟器启动已验证，厂商后台限制、锁屏行为和真实设备 Gate 仍待补测。

### 交付

- 登录
- 工作台
- 可接单/暂停
- 当前可接人数
- 当前定位状态
- 专属二维码
- 待确认订单
- 接受/拒绝
- 多订单列表
- 履约推进
- 金额录入
- Mock 收款

### 技术重点

- 前台定位服务
- PushProvider 接口
- 实时通道
- 锁屏/后台通知
- 弱网处理

### Gate

至少使用真实 Android 设备跑通：

```text
H5 下单
→ 后台派单
→ 司机锁屏收到提醒
→ 接受
→ 履约
→ 到达目的地
```

## Phase 4 — 支付领域与真实渠道接入

### 先完成

- Payment
- PaymentAttempt
- MockPaymentProvider
- 付款 Token 页面
- 回调幂等框架

### 商户资料齐全后

- WeChatPayProvider
- AlipayProvider
- 主动查询补偿
- 真实渠道支付异常后台
- 本地人工退款异常登记/解决/驳回后台（已完成）
- 对账能力

### Gate

- 重复回调不重复入账。
- 页面关闭仍可依赖服务端确认结果。
- 真实沙箱/测试商户闭环通过。

## Phase 5 — Driver Settlement & Withdrawal

### 交付

- DriverAccount
- DriverLedger
- 业务收入
- 可提现余额
- 提现申请
- 冻结余额
- 审核/驳回
- 待打款
- 人工打款确认
- 线下收款修正
- 财务审计

### Gate

资金不变量自动化全部 PASS；每笔余额都可从 Ledger 解释。

## Phase 6 — 生产硬化

### 交付

- HTTPS
- 配置/密钥管理
- DB 自动备份
- 恢复演练
- 日志脱敏
- 告警
- 限频
- 文件安全
- 生产支付配置
- Android 主流真机验证
- 生产上线准备清单逐项确认

### Gate

没有“只能在开发机运行”的关键能力。

## Phase 7 — 现实灰度

建议：

1. 3~5 名真实司机；
2. 10~20 名；
3. 50 名；
4. 全量。

每次扩大前统计：

- 新单通知是否可靠；
- 定位是否稳定；
- 调度员是否能快速找到未处理单；
- 是否出现重复单；
- 是否出现司机/订单状态不同步；
- 是否出现支付/余额差异；
- 人工异常流程是否够用。

## 不建议的开发方式

- 先做完整漂亮 Dashboard，再补状态一致性。
- 四个端同时写大量假数据页面。
- 真实支付先于 Payment/Ledger 领域设计。
- 为“以后可能有很多司机”提前拆微服务。
- 未确认 PRD 变更直接硬编码。

## 当前下一步

当前 Phase 3 本地垂直切片已完成：Android 司机端登录、接单/拒单、履约、金额、定位、收入/提现和本地新单通知均已通过模拟器 Gate。下一步进入 Phase 4 外部渠道与部署准备：真实支付/退款、Push 厂商、MySQL/HTTPS、备份告警和服务器环境仍需正式资料与验收。
