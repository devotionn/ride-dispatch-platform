# Passenger H5

乘客轻量 H5，覆盖公共预约、司机二维码定向预约、订单状态查询和接单前取消。

## 路由

- `/ride` — 公共预约入口。
- `/ride/d/:driverShortCode` — 司机专属二维码定向预约入口。
- `/order/:orderNo` — 乘客订单状态页。

## 本地启动

要求 Node.js 22.18+，后端默认运行在 8080 端口。

```bash
cd passenger-h5
cp .env.example .env.local
pnpm install --frozen-lockfile
pnpm run dev
```

Vite 开发环境默认把 `/api` 代理到 `http://localhost:8080`。如本机后端使用其他端口，可在 `.env.local` 设置 `VITE_DEV_API_TARGET` 覆盖代理目标；如果生产环境前后端不同域，可设置 `VITE_API_BASE_URL`。

## 地点与坐标

H5 不集成商业地图 SDK，也不需要地图 Key。乘客可以选择浏览器原生定位、搜索系统维护的常用地点，或手工输入任意地址。经纬度是可选的 WGS84 信息；未获得坐标时仍可提交订单，后台会使用人工派单。

## 构建校验

```bash
pnpm run typecheck
pnpm run build
```
