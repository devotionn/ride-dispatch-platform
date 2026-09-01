# 生产部署指南（Docker Compose 栈）

本指南描述使用仓库自带的 Docker Compose 生产栈，把系统部署到一台 Linux 服务器（推荐 Asia/Shanghai 时区）的完整流程。目标规模 ≤ 300 名司机，架构保持：模块化单体 + MySQL 8.4 + Nginx + Docker。

## 栈结构

```text
deploy/docker-compose.production.yml
├─ mysql    mysql:8.4            内部网络，不发布 3306 端口
├─ backend  Spring Boot 生产 JRE  仅内部网络，Nginx 反代
└─ nginx    nginx:1.28-alpine    80/443 乘客端 + API，8443 管理端
```

- 前端产物（Passenger H5、Admin Web）在 `deploy/production/nginx/Dockerfile` 的多阶段构建中用 pnpm 编译并直接打进 nginx 镜像，服务器上不需要 Node。
- MySQL 数据与平台 Logo 上传目录分别使用 named volume `mysql-data`、`brand-data`，`docker compose down` 不会丢数据。
- 所有服务 `restart: unless-stopped`；backend 通过 healthcheck 等待 mysql healthy 后启动；nginx 等待 backend healthy。

## 首次部署

前置条件：Linux 服务器已安装 Docker Engine 与 compose 插件；域名 A 记录已指向服务器（如暂无域名，可先用自签证书按[HTTPS 指南](https-and-domain-setup.md)跑通本地/内网）。

```bash
git clone <仓库地址>
cd ride-dispatch-platform

# 1. 准备环境变量
cp deploy/production/.env.example deploy/production/.env
vi deploy/production/.env          # 全部 CHANGE_ME 必须替换；见 environment-variables.md

# 2. 放置 TLS 证书
mkdir -p deploy/production/certs
#    fullchain.pem + privkey.pem → deploy/production/certs/
#    尚无证书时，见 https-and-domain-setup.md 的 certbot 流程

# 3. 构建 + 启动 + 等待健康
./deploy/scripts/deploy.sh

# 4. 冒烟验证
SMOKE_BASE_URL="https://<你的域名>" ./deploy/scripts/smoke-production.sh
```

`deploy.sh` 会：检查 docker/compose、校验 `.env` 无 CHANGE_ME 残留、`compose build`、`compose up -d`、轮询等待 backend healthy。任一步失败立即退出非 0。

## 首次登录与密码轮换

- `.env` 中的 `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` 只在 `admin_user` 表为空时创建首个管理员。
- 生产 profile 会拒绝 `admin123`、`123456` 等已知默认密码（启动即失败）。
- 首次登录成功后立即在管理端修改密码，然后从 `.env` 中清空这两个变量并重新 `docker compose up -d backend` 使其不再出现在进程环境里。

## 访问入口

| 入口 | 地址 | 说明 |
| --- | --- | --- |
| 乘客端 | `https://<域名>/` | H5 SPA |
| 公共 API | `https://<域名>/api/…` | Nginx 反代 backend |
| 司机 SSE | `https://<域名>/api/v1/driver/events` | 已关闭 Nginx 缓冲 |
| 管理端 | `https://<域名>:8443/` | 云安全组应只放行管理员 IP |

后端 8080 与 MySQL 3306 都只在 compose 内部网络，未发布到宿主机。

## 日常运维

```bash
docker compose -f deploy/docker-compose.production.yml --env-file deploy/production/.env ps
docker compose -f deploy/docker-compose.production.yml --env-file deploy/production/.env logs -f backend
./deploy/scripts/backup-mysql.sh          # 备份（默认保留 14 天）
./deploy/scripts/restore-mysql.sh <file>  # 恢复（需确认，见 runbook）
```

## 后续版本更新

```bash
git pull
./deploy/scripts/deploy.sh          # 重建镜像并滚动重启
```

回滚见[Rollback Runbook](rollback-runbook.md)。

## 服务器时区

推荐宿主机与所有容器统一 `Asia/Shanghai`（compose 已对 mysql/backend 设置 `TZ`）。数据库内 `TIMESTAMP(6)` 列与 Hibernate 的 Instant 绑定语义保持现状（JDBC `serverTimezone=Asia/Shanghai`、Hibernate `jdbc.time_zone: UTC`），不要在业务侧改动。

## 附录：测试服务器参考配置（已脱敏）

以下内容保留部署方法，不包含真实公网地址或主机身份：

- 部署目录示例：`/opt/ride-dispatch-docker/`（compose 文件、production/.env、certs、scripts）。`.env` 与证书只存在于服务器，不在 Git。
- 如服务器无法直连 Docker Hub，可在构建机 `docker save | gzip` 后上传并 `docker load`。
- 端口示例：80（HTTP→HTTPS）、443（乘客端+API）、8088（管理端历史兼容端口）。
- 自签证书测试时可使用 `203.0.113.10` 等 RFC 5737 文档地址作为说明，不应把真实服务器 IP 写入仓库。
- 旧版 systemd 部署迁移至容器后，应保留迁移前数据库快照，并按 rollback runbook 维护回退路径。
- 数据迁移由 Flyway 前向执行，迁移前必须备份并验证业务数据完整性。

常规运维（备份/恢复/冒烟）直接在服务器上执行：

```bash
cd /opt/ride-dispatch-docker
bash deploy/scripts/backup-mysql.sh
SMOKE_BASE_URL="https://127.0.0.1" SMOKE_ADMIN_URL="https://127.0.0.1:8088" SMOKE_INSECURE_TLS=true \
  bash deploy/scripts/smoke-production.sh
```
