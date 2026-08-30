# 部署与运维基线 V1.0

- 状态：DRAFT
- 原则：小规模不代表可以没有备份、日志和回滚；不为了 300 司机规模引入 Kubernetes。

## 1. 环境划分

建议至少：

- local：开发者本机；
- dev：共享开发/联调；
- staging：接近生产配置，用于 UAT、真机和支付沙箱；
- production：真实业务。

不同环境数据库、支付密钥、推送配置严格隔离。

## 2. 本地开发

Docker Compose 建议提供：

- MySQL
- 后端
- 可选 Nginx

Web/Android 可按开发体验独立启动。

数据库初始化全部通过 Flyway。

当前本地后端基线为 Java 21，默认端口为 `8080`。开发机可以使用更高版本 JDK（例如 Java 25）运行，但 CI、Docker 构建和生产运行时仍以 Java 21 为准，除非完成一次明确的全环境升级和验证。

使用 `local` profile 时，开发环境可以使用 H2 和本地种子数据；该 profile 不得用于测试或生产。使用 MySQL 联调时，先启动 `deploy/docker-compose.dev.yml` 中的 MySQL，再由 Flyway 建立 schema。

本地后端可用脚本重复启动和验收：

```powershell
./deploy/scripts/build-local.ps1
./deploy/scripts/launch-local.ps1
./deploy/scripts/smoke-local.ps1
./deploy/scripts/stop-local.ps1
```

脚本默认使用 `8081`，要求 `JAVA_HOME` 指向 Java 21、`MAVEN_HOME` 指向 Maven 3.9+；`smoke-local.ps1` 应在刚重启的本地 H2 实例上运行，以保证账本断言从干净种子开始。

## 3. 生产最小拓扑

```text
                 Internet
                    │
                 HTTPS
                    │
                  Nginx
          ┌─────────┴─────────┐
          │                   │
   passenger/admin      Spring Boot
                              │
                         Private DB
                              │
                            MySQL
```

静态资源/Logo 可使用对象存储或受控本地存储。

## 4. TLS 与域名

- 生产全部 HTTPS。
- HTTP 跳转 HTTPS。
- HSTS 等策略在确认兼容性后启用。
- 管理后台尽量使用独立路径/域名并加强登录保护。
- 支付回调使用固定 HTTPS 地址。

## 5. 配置与密钥

不得提交到 Git：

- DB 密码；
- JWT/Token 签名密钥；
- 微信/支付宝私钥；
- Push 密钥；
- （当前无商业地图依赖）不配置地图服务 Secret；
- 对象存储 Secret。

生产使用环境变量、Secret 管理或服务器受限配置文件。

## 6. 数据库

### 6.1 权限

应用账号仅获得必要 schema 权限；禁止使用 root 作为长期应用账号。

### 6.2 备份

最低要求：

- 自动定期备份；
- 保留多版本；
- 与应用主机故障域隔离；
- 有恢复步骤文档；
- 定期恢复演练。

支付、账本、提现数据的备份优先级最高。

### 6.3 迁移

当前分支的 V011 是为 H2/MySQL 方言兼容编写的 Java migration，并对已完成列变更和地点表创建做防重复检查。该分支尚未作为正式版本发布；若环境曾执行过历史提交 `20beaad` 的 SQL V011，升级前必须先核对 `flyway_schema_history` 与实际 schema，并由发布负责人执行一次受控 `flyway repair`，不得直接覆盖或盲目 repair。

部署顺序必须考虑数据库向前兼容：

```text
备份/确认恢复点
→ 执行兼容迁移
→ 部署应用
→ 健康检查
→ 业务冒烟
```

破坏性字段删除应延迟到旧版本完全退出之后。

## 7. 日志

至少分：

- application log
- access log
- audit log（数据库事实，不只日志文件）
- payment provider log（脱敏）

日志必须带 requestId/traceId。

禁止输出：

- 完整密码/Token；
- 支付私钥；
- 完整银行卡/提现账号；
- 不必要的完整手机号；
- 第三方回调中的敏感原文。

## 8. 监控

首期最值得监控：

- API 5xx；
- 登录失败异常升高；
- 订单创建失败；
- 派单接受失败异常；
- stale driver location 数量；
- 支付回调失败/签名失败；
- Payment 与 Order 状态不一致；
- Ledger 入账失败；
- 提现异常；
- MySQL 磁盘/连接数/慢查询；
- JVM 内存和进程健康。

不要用复杂大盘替代真正告警。

## 9. 健康检查

后端提供：

- liveness
- readiness
- 数据库连接健康

外部依赖故障不一定让整个应用 liveness 失败，应区分“服务进程死亡”和“某 Provider 不可用”。

## 10. 发布

推荐可重复脚本化：

1. 构建不可变制品；
2. 保存版本/commit SHA；
3. 数据库迁移；
4. 启动新版本；
5. health check；
6. API 冒烟；
7. H5/Admin 静态资源确认；
8. 真机基本登录/拉单检查。

## 11. 回滚

- 应用必须能够回滚到上一个已知制品。
- 数据库迁移优先采用向前修复策略，避免依赖危险自动 down migration。
- 支付/账本已经产生的新事实不能通过部署回滚删除。

## 12. 支付故障

第三方支付不可用时：

- 不伪造成功；
- 明确提示不可用；
- 已发起交易通过 Provider 查询/回调最终确认；
- 保留人工异常处理入口。

## 13. 推送故障

Push Provider 故障：

- 不影响订单数据库创建；
- 后台可看见通知失败/待处理；
- App 在线实时通道可作为补充；
- 必要时调度员采用现实人工联系方式处理。

## 14. 安全

- 依赖定期升级；
- 登录限频；
- 后台关键操作 CSRF/会话策略按实际认证模型处理；
- 上传 Logo 校验 MIME、尺寸和扩展名；
- 所有对象级权限后端校验；
- SQL 参数化；
- 对外错误不泄漏堆栈。

## 15. 生产数据

- 开发环境禁止直接使用生产敏感数据。
- 排障需要数据时先脱敏。
- 生产数据库访问应最小授权并留审计。

## 16. 容量判断

约 300 司机即使每 15~60 秒上传一次位置，平均请求规模仍适合普通 Spring Boot + MySQL 架构。

优化顺序应是：

1. 正确索引；
2. 慢 SQL；
3. 连接池；
4. 热点缓存（真正需要时）；
5. 再考虑更复杂架构。

不要倒序。
