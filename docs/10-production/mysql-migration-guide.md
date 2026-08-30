# MySQL 迁移指南（Flyway）

## Profile 与数据库的对应关系

```text
local / test  →  H2 内存库（MODE=MySQL），开发与 CI 快速验证
production    →  MySQL 8.4，Flyway 启动时自动执行迁移
```

- 迁移脚本：`server/src/main/resources/db/migration/V001 … V010`（SQL）加 `server/src/main/java/db/migration/V011、V012`（Java 迁移，用于抹平 H2 与 MySQL 的方言差异），共 12 个版本。
- 生产 profile（`application-production.yml`）数据源无默认凭据：`DB_PASSWORD` 缺失会直接启动失败，不存在静默 H2 回退。
- JPA `ddl-auto: validate`：Hibernate 只校验不建表，schema 完全由 Flyway 管理。

## 空库首启（已在 MySQL 8.4 实测通过）

1. MySQL 容器首次启动会以 `.env` 中的 `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` 创建库和账号，默认字符集 utf8mb4。
2. Backend 以 `SPRING_PROFILES_ACTIVE=production` 启动，Flyway 依次执行 V001–V012，`flyway_schema_history` 中 12 条记录全部 `success=1`。
3. 再次重启（同一数据库）：Flyway 校验 checksum 后不做任何变更，backend 正常 UP。

以上完整流程（含第二次启动、utf8mb4 中文写入回读、重启数据存活）由 CI 的 `Production Infra CI → MySQL Flyway migration gate` 与本地 Docker Gate 覆盖。

## 迁移规范

- 新迁移只能追加 `V0NN__*.sql`，禁止修改已发布脚本的 checksum。
- 迁移 SQL 必须同时兼容 MySQL 8.4；本地开发可用 Docker MySQL 验证（`deploy/docker-compose.dev.yml`）。
- H2 测试通过不代表 MySQL 一定通过；涉及 schema 的改动合入前应观察 MySQL Flyway gate 的结果。

## 关于历史库与 Flyway repair

如果接手一个已有数据但 `flyway_schema_history` 异常（如 checksum 不匹配）的旧数据库：

1. 不要执行 `flyway repair`，先人工核对：`flyway_schema_history` 内容、实际 schema、失败记录。
2. 确认差异原因后，才允许给出受控的修复方案并人工执行。
3. 任何情况下都禁止对生产库运行 `flyway clean` / `DROP DATABASE`。

## 应用回滚 ≠ 数据库回滚

Flyway 迁移不可逆。应用回滚到旧版本时数据库保持在最新 schema；只有向前兼容的 schema（新版本迁移只加列/加表）才能安全回滚应用。禁止自动执行任何 downgrade。详见[Rollback Runbook](rollback-runbook.md)。
