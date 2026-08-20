# Ride Dispatch Platform

面向约 300 名司机以内的小型预约接送/人工调度车队的业务系统。

本仓库以 **PRD V1.3** 为当前业务需求开发基线，目标不是复制大型自动网约车平台，而是把现实车队中的乘客下单、司机定向订单、后台人工派单、司机履约、收款结算、提现、异常处理和审计流程数字化。

## 当前状态

- 仓库阶段：文档基线整理期
- 产品基线：PRD V1.3
- 技术基线：Technical Implementation V1.0
- 当前不代表已完成生产合规评审，也不代表真实支付商户/监管接口已经确定
- 产品代码尚未正式初始化

## 产品组成

- 乘客轻量 H5：公共入口、司机定向入口、订单状态、取消、付款
- 司机 Android App：接单状态、位置、专属二维码、接受/拒绝、履约、收款、收入与提现
- PC 管理后台：司机管理、后台建单、附近司机、人工派单/改派、订单、支付、提现、审计
- Spring Boot 后端：订单状态、调度、定位、支付、账本、权限、审计与通知

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

## 计划中的代码结构

```text
ride-dispatch-platform/
├─ server/             # Spring Boot 模块化单体
├─ passenger-h5/       # Vue 3 + Vant
├─ admin-web/          # Vue 3 + Element Plus
├─ driver-app/         # Kotlin Android
├─ docs/               # 项目文档
└─ deploy/             # 本地/服务器部署文件
```

下一步以文档基线为依据初始化工程骨架和数据库迁移。