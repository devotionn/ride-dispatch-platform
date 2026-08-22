# Admin Web

PC 调度管理后台，面向管理员和调度员，首期重点是订单人工调度与司机管理。

## 已实现工作面

- 管理员/调度员登录；
- 平台品牌名称 / Logo URL 管理（管理员可修改，其他角色只读）；
- 服务端可撤销 Bearer Session；
- 订单列表、状态筛选和分页；
- 后台代客建单；
- 订单详情、派单历史和履约时间线；
- A 点 10km 附近有效司机；
- 人工首次派单；
- 待司机确认订单改派；
- 司机列表和新增司机/车辆；
- 司机专属下单链接复制；
- 高德地图 Provider 与选点组件。

## 本地启动

要求 Node.js 22.12+，后端默认运行在 8080 端口。

```bash
cd admin-web
cp .env.example .env.local
pnpm install --frozen-lockfile
pnpm run dev
```

默认地址 `http://localhost:5174`，开发环境 `/api` 代理到 `http://localhost:8080`。

## 地图

Admin 与 Passenger H5 使用同一高德/GCJ-02 坐标口径。生产环境通过 `deploy/nginx/amap-service.conf.template` 代理高德安全密钥：

```dotenv
VITE_AMAP_KEY=你的WebKey
VITE_AMAP_SERVICE_HOST=https://你的域名/_AMapService
```

真实 `securityJsCode` 只保存在服务器部署 Secret，不写入前端源码或 Git。

## 构建

```bash
pnpm run typecheck
pnpm run build
```
