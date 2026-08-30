# 本地 / Staging / Production 部署工具

## 生产 Docker Compose 栈（推荐）

生产级三服务栈（mysql:8.4 + Spring Boot backend + nginx，前端产物已打进镜像）：

```bash
cp deploy/production/.env.example deploy/production/.env   # 填写真实值
mkdir -p deploy/production/certs                           # 放入 fullchain.pem / privkey.pem
./deploy/scripts/deploy.sh                                 # build + up + 等待健康
SMOKE_BASE_URL="https://localhost" SMOKE_INSECURE_TLS=true ./deploy/scripts/smoke-production.sh
```

配套脚本（均在 `deploy/scripts/`，凭据从 `.env` 读取）：

| 脚本 | 用途 |
| --- | --- |
| `deploy.sh` | 检查 env → build → up → 等待 backend healthy（保留 `:rollback` 镜像 tag） |
| `backup-mysql.sh` | mysqldump gzip 时间戳备份，保留 14 天 |
| `restore-mysql.sh` | 覆盖式恢复，需键入 `RESTORE` 确认或 `--force` |
| `smoke-production.sh` | 生产冒烟（默认只读） |

完整文档：`docs/10-production/`（部署指南、环境变量、迁移、备份恢复、HTTPS、回滚）。

## 已验证的公网服务器部署（systemd 模式，保留）

当前服务器部署采用 Java 17 + MySQL 8 + systemd + Nginx，适合 3.5 GiB 内存的小型云主机：

- 乘客端：`http://<服务器公网IP>/`
- 管理端：`http://<服务器公网IP>:8088/`
- 后端 API：由 Nginx 的 `/api/` 反向代理到本机 `127.0.0.1:8080`
- SSE：`/api/v1/driver/events` 已关闭 Nginx 缓冲

生产 JAR 使用 Java 17 目标字节码构建；测试源码仍按 Java 21 执行：

```powershell
cd server
$env:JAVA_HOME = 'C:\path\to\jdk-21'
mvn clean package -Dmaven.test.skip=true -Dmaven.compiler.release=17 -Djava.version=17 -B
```

将 `target/ride-dispatch-server-0.1.0-SNAPSHOT.jar` 上传为 `/opt/ride-dispatch/app/ride-dispatch-server.jar`，并安装 [systemd 服务模板](systemd/ride-dispatch.service)。服务环境变量放在 `/etc/ride-dispatch-server.env`，不要提交到 Git；至少包含数据库连接、`BRAND_LOGO_UPLOAD_DIR`、首次管理员引导账号和随机密码。

Nginx 配置需要放行两个站点端口（默认 80、8088），并在阿里云安全组开放对应 TCP 入站规则。后端 8080 只监听回环地址，不应直接暴露公网。

首次启动后检查：

```bash
systemctl is-active mysqld ride-dispatch nginx
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1/api/v1/public/brand
```

完成首次登录后应立即修改/轮换引导管理员密码，并删除或清空引导变量；公网正式环境还应配置域名、HTTPS、云安全组白名单、日志轮转和异地备份。

### 公网打不开时的判断

如果服务器本机的以下检查均成功：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/
```

但外部访问超时，且 `/var/log/nginx/access.log` 没有对应请求，问题不在前端或后端，而在云平台入站规则。阿里云 ECS 安全组至少添加：

| 方向 | 协议 | 端口 | 来源 |
| --- | --- | --- | --- |
| 入方向 | TCP | 80 | `0.0.0.0/0` 或指定用户公网 IP |
| 入方向 | TCP | 8088 | 建议只允许管理员公网 IP |

不要为了访问页面开放后端 8080；它应继续只监听 `127.0.0.1`。保存安全组规则后，再从外部网络访问乘客端 80 和管理端 8088。

## MySQL 开发容器

```powershell
docker compose -f deploy/docker-compose.dev.yml up -d mysql
docker compose -f deploy/docker-compose.dev.yml ps
```

容器健康后，默认账号为 `ride`，数据库为 `ride_dispatch`。后端默认生产配置使用 MySQL；本机联调仍可用 `local` profile 的 H2。

## 备份

```powershell
$env:MYSQL_ROOT_PASSWORD = 'root_dev_password'
./deploy/scripts/backup-mysql.ps1
```

也可以显式指定输出文件：

```powershell
./deploy/scripts/backup-mysql.ps1 -RootPassword 'root_dev_password' -OutputFile 'D:/backups/ride-dispatch.sql'
```

## 恢复

恢复是覆盖性操作，脚本要求显式确认：

```powershell
./deploy/scripts/restore-mysql.ps1 `
  -RootPassword 'root_dev_password' `
  -BackupFile 'D:/backups/ride-dispatch.sql' `
  -ConfirmRestore
```

正式环境还需要将备份复制到独立存储，并完成支付/账本/提现恢复后的业务核对；本地脚本只负责可重复的数据库导出和恢复入口。
