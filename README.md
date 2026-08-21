# Ride Dispatch Platform

面向约 300 名司机以内的小型预约接送/人工调度车队的业务系统。

本仓库以 **PRD V1.3** 为当前业务需求开发基线。目标不是复制大型自动网约车平台，而是把现实车队中的乘客下单、司机定向订单、后台人工派单、司机履约、收款结算、提现、异常处理和审计流程数字化。

## 当前状态

- 产品基线：PRD V1.3
- 技术基线：Technical Implementation V1.0
- Phase 1 Backend Core：核心订单/司机/定位/派单/认证/履约 API 已实现，已验证主链路 CI PASS
- Phase 2：Passenger H5 + Admin Web 开发中
- 真实微信/支付宝商户、Android Push 厂商、监管接口等外部生产资料尚未确定
- 已接单/执行中强制改派的责任交接语义仍按 PRD V1.4 Proposal 管理，不擅自固化

## 已实现代码结构

```text
ride-dispatch-platform/
├─ server/             # Java 21 + Spring Boot 模块化单体
├─ passenger-h5/       # Vue 3 + Vant 乘客 H5
├─ admin-web/          # Vue 3 + Element Plus PC 调度后台
├─ driver-app/         # Kotlin Android（后续 Phase）
├─ docs/               # 产品/技术/测试/运维完整基线
└─ deploy/             # Docker / Nginx / 外部服务安全代理
```

## Backend Core

当前已经覆盖：

- Admin / Driver 登录与可撤销数据库 Bearer Session；
- ADMIN / DISPATCHER / FINANCE / DRIVER 权限边界；
- Driver / Vehicle / Current Location；
- 公共 H5、司机二维码、后台代客三种订单入口；
- Passenger Access Token；
- Passenger 下单 `Idempotency-Key` 防弱网重复订单；
- 10km + 位置 5 分钟有效 + 可接人数附近司机筛选；
- 人工派单、司机接受/拒绝、待确认改派；
- DispatchAttempt 完整历史；
- 四段式履约状态和 OrderProgressEvent；
- 最终金额进入 `PENDING_PAYMENT`；
- OperationLog 审计；
- Flyway 数据库迁移；
- OpenAPI / Swagger；
- Spring 集成测试与 GitHub Actions Backend CI。

## Passenger H5

当前 Phase 2 已实现：

- `/ride` 公共预约；
- `/ride/d/:driverShortCode` 司机定向预约；
- 安全的司机公开信息展示；
- `/order/:orderNo` 订单状态；
- Passenger Token 本机保存与恢复；
- 接单前取消；
- 订单状态自动刷新；
- 高德 JS API 2.0 POI 搜索、定位、逆地理、地图选点；
- 无地图配置时手工地址/经纬度联调兜底；
- 弱网自动重试 + 同一 Idempotency-Key；
- 高德 `securityJsCode` Nginx 服务端安全代理模板。

## Admin Web

当前 Phase 2 已实现第一版工作台：

- 管理员/调度员登录；
- 订单中心、状态筛选和分页；
- 后台代客建单；
- 订单详情；
- 附近司机；
- 人工派单 / 待确认改派；
- 派单历史和履约时间线；
- 司机列表；
- 新增司机与车辆；
- 司机专属下单链接；
- Admin 高德地图 Provider / 选点能力；
- 独立 Admin Web CI Gate。

## 核心原则

1. **人工调度优先**：系统筛选、排序、提醒；最终派单决策由调度员执行。
2. **业务严谨，架构简单**：首期采用模块化单体，不引入不必要的微服务、MQ、搜索集群。
3. **订单和资金必须可追溯**：派单尝试、状态变化、支付、余额变化、提现、人工修正均保留记录。
4. **已确认需求与实施建议分离**：PRD V1.3 不被技术文档静默修改；建议变更进入 V1.4 候选清单。
5. **真实环境优先**：考虑 Android 后台限制、弱网、重复点击、支付回调、司机定位失效、人工异常处理和灰度上线。

## 文档导航

| 文档 | 作用 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档总索引、版本与变更规则 |
| [PRD V1.3 开发基线](docs/01-product/PRD-V1.3-baseline.md) | 当前已确认的产品需求基线 |
| [技术实施与现实落地方案 V1.0](docs/02-implementation/technical-implementation-v1.0.md) | 总体架构、技术选型和实施原则 |
| [核心领域与业务规则 V1.0](docs/03-domain/domain-rules-v1.0.md) | 订单、调度、履约、支付、资金和异常规则 |
| [数据模型设计 V1.0](docs/04-data/data-model-v1.0.md) | 数据实体、关系、约束、索引和资金一致性 |
| [API 契约草案 V1.0](docs/05-api/api-contract-v1.0.md) | API 分层、鉴权、幂等、错误与关键接口 |
| [测试与验收策略 V1.0](docs/06-testing/acceptance-and-test-strategy-v1.0.md) | PRD 验收映射、自动化、并发和真机测试 |
| [部署与运维基线 V1.0](docs/07-ops/deployment-and-operations-v1.0.md) | 环境、发布、备份、日志、监控和恢复 |
| [开发路线图 V1.0](docs/08-roadmap/development-roadmap-v1.0.md) | 分阶段实现顺序、交付物和 Gate |
| [PRD V1.4 待确认建议](docs/09-decisions/PRD-V1.4-change-proposals.md) | 现实落地中发现但尚未写回 V1.3 的事项 |
| [生产上线准备清单 V1.0](docs/10-production/production-readiness-checklist-v1.0.md) | 真钱、真司机、正式上线前的检查项 |

## 文档权威顺序

发生冲突时，当前按以下顺序处理：

1. 已正式确认并版本化的 PRD；
2. 已正式确认的业务决策记录；
3. 技术实施文档；
4. API/数据模型草案；
5. PRD 变更建议和讨论稿。

未确认的建议不得被当作既定产品需求。
