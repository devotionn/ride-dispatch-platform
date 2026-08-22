# Passenger H5

乘客轻量 H5，覆盖公共预约、司机二维码定向预约、订单状态查询和接单前取消。

## 路由

- `/ride` — 公共预约入口。
- `/ride/d/:driverShortCode` — 司机专属二维码定向预约入口。
- `/order/:orderNo` — 乘客订单状态页。

## 本地启动

要求 Node.js 22.12+，后端默认运行在 8080 端口。

```bash
cd passenger-h5
cp .env.example .env.local
pnpm install --frozen-lockfile
pnpm run dev
```

Vite 开发环境默认把 `/api` 代理到 `http://localhost:8080`。如果生产环境前后端不同域，可设置 `VITE_API_BASE_URL`。

## 地图接入

H5 使用高德地图 JavaScript API 2.0。地图能力隔离在 `src/map/`，业务页面只消费 `MapPoint`：

- POI 搜索；
- 地图点击选点；
- 高德 Geolocation 定位；
- 逆地理编码；
- 无地图配置时的手工地址/经纬度联调兜底。

### 开发环境

申请高德 Web（JS API）Key 后填写：

```dotenv
VITE_AMAP_KEY=你的WebKey
```

2021-12-02 之后创建的 Key 需要配合安全密钥。生产环境不要把 `securityJsCode` 写入 Vite 环境变量或前端源码。

### 生产环境

按高德官方推荐的代理服务器转发模式：

1. 安全密钥只保存在服务器部署 Secret 中；
2. Nginx 使用 `deploy/nginx/amap-service.conf.template`；
3. 部署时渲染 `${AMAP_SECURITY_JSCODE}`；
4. H5 配置：

```dotenv
VITE_AMAP_KEY=你的WebKey
VITE_AMAP_SERVICE_HOST=https://你的域名/_AMapService
```

`/_AMapService` 是高德 JS API 安全代理模式约定的固定前缀。

官方参考：

- https://lbs.amap.com/api/javascript-api-v2/guide/abc/load
- https://lbs.amap.com/api/javascript-api-v2/guide/abc/jscode

## 坐标一致性

乘客地图选点和定位都通过高德 JS API 获取，避免浏览器原生 WGS84 定位与后续 Android 高德定位混用造成坐标系偏差。后端保存的业务经纬度应保持同一高德/GCJ-02 口径。

## 构建校验

```bash
pnpm run typecheck
pnpm run build
```
