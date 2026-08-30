# 生产冒烟测试

脚本：`deploy/scripts/smoke-production.sh`。默认全部只读，不创建任何业务数据。

## 检查项

| # | 检查 | 说明 |
| --- | --- | --- |
| 1 | 乘客端首页 | `GET /` 返回 H5 SPA HTML |
| 2 | 乘客端 SPA fallback | `GET /order/SPA-FALLBACK-CHECK` 返回应用而非 404 |
| 3 | 公共品牌 API | `GET /api/v1/public/brand` 证明 nginx→backend→MySQL 读链路 |
| 4 | 管理端登录 API | 错误凭据 `POST /api/v1/auth/admin/login` 必须返回 401 |
| 5 | 管理端首页 | `GET :8443/` 返回 Admin SPA |
| 6 | 管理端 SPA fallback | `GET :8443/orders` 返回应用 |
| 7 | （可选）真实登录 | 提供凭据时执行真实管理员登录 + 只读 API + 登出 |

## 用法

```bash
# 本地/内网自签证书
SMOKE_BASE_URL="https://localhost" SMOKE_INSECURE_TLS=true \
  ./deploy/scripts/smoke-production.sh

# 正式域名
SMOKE_BASE_URL="https://example.com" \
  SMOKE_ADMIN_USERNAME=<管理员账号> SMOKE_ADMIN_PASSWORD=<管理员密码> \
  ./deploy/scripts/smoke-production.sh
```

- `SMOKE_ADMIN_URL`：管理端地址，默认 `<SMOKE_BASE_URL>:8443`。
- `SMOKE_INSECURE_TLS=true`：仅在自签证书联调时使用。
- 任一检查失败脚本以非 0 退出。

## 创建业务数据

脚本不会创建订单等脏数据（公共建单需要完整业务上下文，且清理成本高）。需要验证完整下单链路时，用 `e2e/` 中已有的浏览器/HTTP 验收脚本对测试环境执行，不要直接对生产跑。

## 2026-08-30 本地生产栈冒烟结果

本地 Docker Gate（自签证书，`https://localhost`）全部 6 项必查 PASS，真实登录验证 PASS（使用临时 bootstrap 管理员），记录见最终交付报告。
