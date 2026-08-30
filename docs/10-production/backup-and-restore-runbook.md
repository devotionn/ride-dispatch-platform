# 备份与恢复 Runbook

脚本位于 `deploy/scripts/`，凭据一律从 `deploy/production/.env` 读取，脚本内不出现密码。

## 备份：backup-mysql.sh

```bash
./deploy/scripts/backup-mysql.sh
# 可选：--env-file <路径>   --output-dir <目录>
```

- 使用 `mysqldump --single-transaction --quick --default-character-set=utf8mb4` 通过容器执行，gzip 压缩。
- 产出：`deploy/production/backups/ride_dispatch_YYYYMMDD_HHMMSS.sql.gz`。
- 校验：文件非空且包含 mysqldump 头，否则删除并返回非 0。
- 保留策略：默认删除 14 天前的 `ride_dispatch_*.sql.gz`（`find -mtime`，只匹配该精确文件名模式，不会误删其他文件）。
- 建议 cron：每天凌晨执行一次，例如 `30 3 * * * /opt/ride-dispatch/deploy/scripts/backup-mysql.sh >> /var/log/ride-dispatch-backup.log 2>&1`。

生产要求备份副本同步到独立存储（另一台机器/对象存储），不要只留在应用服务器本机。

## 恢复：restore-mysql.sh

```bash
./deploy/scripts/restore-mysql.sh deploy/production/backups/ride_dispatch_20260830_130000.sql.gz
# 强制执行（跳过确认）：追加 --force
```

行为：

1. 校验备份文件存在；缺失退出非 0。
2. 默认交互确认：必须键入 `RESTORE` 才继续；`--force` 跳过。
3. `DROP DATABASE` + `CREATE DATABASE … utf8mb4` 后导入 dump。
4. 任何一步失败脚本以非 0 退出。

**恢复是覆盖性操作。** 生产执行前：先做一次当前库的新备份；停止写入口（如停止 nginx 或 backend）避免恢复过程中新数据写入。

## 恢复演练（已完成一次真实演练）

2026-08-30 在本机 Docker 生产栈完成演练，流程与结果记录在 [production-smoke-checklist.md](production-smoke-checklist.md)：

```text
启动 MySQL → 生成业务数据（管理员 + 中文品牌/地点）→ backup
→ down 后重建空库 → restore → 重启 backend → 验证业务数据与登录
```

演练要点：恢复后 backend 能正常读数据、管理员可用备份时的密码登录，才算成功；只验证 mysqldump 退出码不算完成。
