# 环境变量说明

生产栈的变量统一放在 `deploy/production/.env`（已 gitignore），模板见 [deploy/production/.env.example](../../deploy/production/.env.example)。所有占位值必须替换，`deploy.sh` 会拒绝包含 `CHANGE_ME` 的 env 文件。

## .env 变量

| 变量 | 必填 | 用途 |
| --- | --- | --- |
| `APP_DOMAIN` | 是 | 正式域名（证书签发与文档记录用） |
| `HTTP_PORT` | 否 | 宿主机 80 端口映射，默认 80 |
| `HTTPS_PORT` | 否 | 宿主机 443 端口映射，默认 443 |
| `ADMIN_HTTPS_PORT` | 否 | 管理端 HTTPS 端口，默认 8443；安全组只放行管理员 IP |
| `TZ` | 否 | 容器时区，默认 `Asia/Shanghai` |
| `DB_NAME` | 是 | MySQL 数据库名（容器初始化 + backend 连接） |
| `DB_USERNAME` | 是 | MySQL 业务账号（容器初始化 + backend 连接） |
| `DB_PASSWORD` | 是 | MySQL 业务账号密码 |
| `MYSQL_ROOT_PASSWORD` | 是 | MySQL root 密码（容器初始化、备份/恢复脚本使用） |
| `ADMIN_BOOTSTRAP_USERNAME` | 否 | 首个管理员账号，仅 `admin_user` 为空时生效 |
| `ADMIN_BOOTSTRAP_PASSWORD` | 否 | 首个管理员密码；生产 profile 拒绝已知默认值 |
| `BACKEND_IMAGE` / `NGINX_IMAGE` | 否 | 覆盖镜像 tag，回滚用（见 rollback runbook） |

本系统没有 JWT 签名密钥：访问令牌是数据库会话令牌（Bearer token 存 `auth_sessions`），因此没有 `JWT_SECRET`。支付当前为线下/人工记账模型，没有真实支付网关凭据需要配置；接入真实微信/支付宝商户属于后续阶段（EXTERNAL BLOCKER）。

## 后端 production profile 读取的变量

`server/src/main/resources/application-production.yml`：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库地址；compose 中固定为 `mysql:3306` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭据，无默认值，缺失即启动失败（防 H2 静默回退） |
| `BRAND_LOGO_UPLOAD_DIR` | 平台 Logo 上传目录；compose 固定为 `/app/data/brand`（named volume），无默认值 |
| `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` | 首启管理员引导 |
| `SERVER_PORT` | 默认 8080 |
| `SPRING_PROFILES_ACTIVE` | compose 固定为 `production` |

local/test profile 使用 H2（`application-local.yml`、`application-test.yml`），与生产 MySQL 互不干扰。

## CI 中的密码

GitHub Actions 的 Production Infra CI 只使用一次性随机测试密码（`openssl rand` 生成，不落日志），不存储任何真实 secret。
