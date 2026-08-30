# HTTPS 与域名配置

## 方案

生产 TLS 在 nginx 容器终结：

- `80`：只服务 `/.well-known/acme-challenge/`（certbot webroot 续期），其余 301 → HTTPS。
- `443`：乘客端 + API。
- `8443`：管理端（同一证书；云安全组只放行管理员 IP）。

证书从宿主机 `deploy/production/certs/` 以只读卷挂载到 nginx 容器 `/etc/nginx/certs/`，文件名：

```text
deploy/production/certs/fullchain.pem
deploy/production/certs/privkey.pem
```

TLS 基线：TLSv1.2 + TLSv1.3，HSTS（max-age=15552000），`X-Content-Type-Options`、`X-Frame-Options: SAMEORIGIN`、`Referrer-Policy`。配置在 `deploy/production/nginx/ride-dispatch.prod.conf`，不做更复杂的 TLS 调优。

## Let's Encrypt 签发（推荐 certbot webroot）

服务器上安装 certbot 后：

```bash
# 首次签发（nginx 已用自签/占位证书跑起来，80 端口在服务）
sudo certbot certonly --webroot -w /var/www/certbot -d <你的域名>

# 拷入挂载目录
sudo cp /etc/letsencrypt/live/<你的域名>/fullchain.pem deploy/production/certs/fullchain.pem
sudo cp /etc/letsencrypt/live/<你的域名>/privkey.pem deploy/production/certs/privkey.pem
docker compose -f deploy/docker-compose.production.yml --env-file deploy/production/.env restart nginx

# 续期：crontab 每天检查，续期后拷贝并 reload
# 0 4 * * * certbot renew --quiet && cp -f /etc/letsencrypt/live/<域名>/fullchain.pem /opt/ride-dispatch/deploy/production/certs/ && cp -f /etc/letsencrypt/live/<域名>/privkey.pem /opt/ride-dispatch/deploy/production/certs/ && docker compose -f /opt/ride-dispatch/deploy/docker-compose.production.yml --env-file /opt/ride-dispatch/deploy/production/.env restart nginx
```

首次部署若还没有证书，可先生成 30 天自签证书把栈跑起来（仅限联调，正式域名必须换 Let's Encrypt 或商业证书）：

```bash
mkdir -p deploy/production/certs
openssl req -x509 -newkey rsa:2048 -nodes -days 30 \
  -keyout deploy/production/certs/privkey.pem \
  -out deploy/production/certs/fullchain.pem -subj '/CN=<你的域名>'
```

替代方案：宿主机/云厂商前置 TLS termination（如云负载均衡七层卸载）。此时 nginx 容器可只保留 80 端口并去掉强制 301（按实际情况调整 conf），后端仍只走内部网络。

## 域名与转发头

- nginx 已向 backend 传递 `X-Forwarded-Proto` / `X-Forwarded-For` / `Host`，backend 已配置 `server.forward-headers-strategy: framework`，应用内看到的 scheme/客户端 IP 正确。
- Android Driver App 生产环境必须访问 `https://`；Android release 不依赖 cleartext HTTP，debug 仍可访问本地环境。

## 当前状态

- HTTPS 配置、HTTP→HTTPS 跳转、TLS 1.2+：已在本地 Docker Gate 用自签证书验证（`nginx -t` + 冒烟脚本 `-k`）。
- 真实域名 DNS、Let's Encrypt 正式证书：EXTERNAL / MANUAL，需要真实域名与服务器后执行。
