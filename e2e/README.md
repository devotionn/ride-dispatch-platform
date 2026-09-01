# Phase 2 浏览器验收

这组脚本验证路线图中的 Phase 2 Gate：

```text
场景 1：公共 H5 纯文字地址下单
→ Admin 订单中心出现无坐标提示
→ 调度员从符合资格司机中人工派单

场景 2：公共 H5 浏览器原生定位上车点 + 手工目的地
→ Admin 订单中心出现坐标和距离
→ 调度员按附近司机规则人工派单

场景 3：司机二维码定向 H5 下单
→ 订单锁定司机并进入 PENDING_DRIVER_CONFIRM
```

链路使用浏览器原生定位、Place Catalog 和手工地址，不需要商业地图 SDK 或地图 Key。

`payment-settlement-browser.cjs` 额外验证乘客订单页进入付款页、元金额展示、Mock 成功回调、订单完成、服务端支付状态同步，以及 Admin 支付/提现 CSV 导出和账号脱敏。

脚本会在创建订单前通过司机 API 刷新本地种子司机的位置，确保可以重复运行，同时保留服务端“位置必须在 5 分钟内”的真实筛选规则。脚本不会通过 API 创建订单或代替 Admin 派单。

## 运行前提

- Node.js 22.18+；项目通过 `packageManager` 固定 pnpm 11.19.0；
- 后端运行在 `http://localhost:8080`，使用 `local` profile 时会初始化 H2 和本地演示数据；
- Passenger H5 运行在 `http://localhost:5173`；
- Admin Web 运行在 `http://localhost:5174`；
- 本机安装 Chrome。也可以通过 `BROWSER_CHANNEL` 指定 Playwright 支持的浏览器 channel。

## 启动与运行

```bash
pnpm install --frozen-lockfile
pnpm run phase2
pnpm run payment:browser
```

默认使用 Chrome 无头模式。需要看到浏览器窗口时设置 `HEADLESS=false`。

每次运行前会清理上一次的诊断产物。失败时脚本会把截图和诊断 JSON 写入 `e2e/artifacts/`，包括当前 URL、订单号以及最后一个 API 响应的方法、路径和状态码；其中不保存查询参数、Passenger Token、密码或完整认证响应。

仓库中的 `.github/workflows/phase2-browser-gate.yml` 会在后端、任一 Web 端或 E2E 脚本变更时启动 Java 21 local profile、两个 Vite 开发服务器和 Playwright bundled Chromium，自动执行同一条 Gate。

## 本地 HTTP 深测

`local-http-depth.cjs` 在一个隔离的 local/H2 后端上验证幂等、乘客 Token 越权、接单前取消、司机权限、重复接受、非法履约跳转、金额边界和完整履约链路。默认后端地址为 `http://localhost:8082`，避免污染常驻的开发实例：

```bash
API_URL=http://localhost:8082 pnpm run depth:http
```

PowerShell：

```powershell
$env:API_URL = 'http://localhost:8082'
pnpm run depth:http
```

## 已部署服务器冒烟

`deployed-http-smoke.cjs` 只使用环境变量中的验收账号，不把密码写入仓库；它验证 Nginx `/api` 代理、管理员/司机登录、权限边界、司机账户、公共订单幂等、乘客取消和司机 SSE 连接。该脚本会在服务器数据库创建一条带 `DEPLOYED-SMOKE` 标记并立即取消的测试订单。

```powershell
$env:DEPLOYED_API_URL = 'http://203.0.113.10'
$env:DEPLOYED_ADMIN_USERNAME = 'admin'
$env:DEPLOYED_ADMIN_PASSWORD = '<server bootstrap password>'
$env:DEPLOYED_DRIVER_USERNAME = 'D101'
$env:DEPLOYED_DRIVER_PASSWORD = '<server driver password>'
pnpm run smoke:deployed
```

`203.0.113.10` 属于文档示例地址（TEST-NET-3），实际执行部署回归时必须通过 `DEPLOYED_API_URL` 指向验收环境。

`deployed-core-flow.cjs` 会继续验证线上 MySQL 的派单、四阶段履约、金额（分）写入、乘客支付上下文、司机收入、提现审批/打款和支付异常驳回。正式环境不启用 `local` profile 的 Mock 支付回调，因此脚本会把该接口的结构化 404 作为预期边界，并改走司机线下收款二次确认，保证后半段结算链路仍能在服务器上回归；真实支付宝/微信回调需要另行配置生产商户参数。

```powershell
$env:DEPLOYED_API_URL = 'http://203.0.113.10'
$env:DEPLOYED_ADMIN_USERNAME = 'admin'
$env:DEPLOYED_ADMIN_PASSWORD = '<server bootstrap password>'
$env:DEPLOYED_DRIVER_USERNAME = 'D101'
$env:DEPLOYED_DRIVER_PASSWORD = '<server driver password>'
pnpm run core:deployed
```

`sse:deployed` 会在司机 SSE 已连接的情况下真实派单，断言 `DRIVER_NEW_DISPATCH` 的司机、attemptId、orderNo 均匹配，然后自动拒单并取消测试订单。

```powershell
pnpm run sse:deployed
```
