# Rollback Runbook

目标：单机 compose 栈的简单、可重复回滚。不涉及蓝绿部署或 Kubernetes rollout。

## 应用回滚

镜像 tag 由 `.env` 中 `BACKEND_IMAGE` / `NGINX_IMAGE` 控制（默认 `:local`）。`deploy.sh` 在每次重建前会把当前镜像保留为 `:rollback` 备份 tag。

### 方式 A：用保留的上一版镜像回滚（快）

```bash
cd <仓库目录>
docker tag ride-dispatch-backend:local ride-dispatch-backend:broken-$(date +%Y%m%d)   # 标记坏版本（可选）
docker tag ride-dispatch-backend:rollback ride-dispatch-backend:local
docker tag ride-dispatch-nginx:rollback ride-dispatch-nginx:local
docker compose -f deploy/docker-compose.production.yml --env-file deploy/production/.env up -d backend nginx
```

### 方式 B：从源码重建上一版本

```bash
git fetch --all
git checkout <上一个已验证的 commit 或 tag>
./deploy/scripts/deploy.sh
```

回滚后执行 `deploy/scripts/smoke-production.sh` 确认。

## 数据库不随应用回滚

**应用回滚 ≠ 数据库回滚。** Flyway 迁移不可逆：

- 回滚应用后，数据库保持在最新 schema。只要旧版本应用对"新版本新增的表/列"是向前兼容的（新迁移只加表/加列、不改旧列语义），应用可正常运行。
- 禁止对生产库自动执行 Flyway downgrade / `flyway clean` / 手写 `DROP`。
- 若某次迁移破坏了旧应用兼容性且必须回滚，正确做法是：停止应用 → 用 [restore runbook](backup-and-restore-runbook.md) 把数据库恢复到该迁移之前的备份 → 再回滚应用。恢复前先对当前库做新备份。

## 备份 tag 的产生

`deploy.sh` 每次执行 `compose up -d` 前会 `docker tag` 现有 `:local` 镜像为 `:rollback`（若存在），因此最近一次成功部署的镜像总是可回退的。

## 相关

- 应用回滚只回退代码与静态资源；上传的 Logo 存于 `brand-data` volume，不受影响。
- `docker compose down`（不带 `-v`）不会删除数据卷；确认要清除数据才使用 `down -v`（生产禁止）。
