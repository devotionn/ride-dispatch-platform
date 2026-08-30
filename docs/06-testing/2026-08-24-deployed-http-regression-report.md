# 已部署服务器 HTTP/SSE 回归报告（2026-08-24）

## 环境

- 公网入口：`http://8.138.144.54/`（乘客端）和 `http://8.138.144.54:8088/`（管理端）。
- 后端：Spring Boot 生产 profile，回环监听 `127.0.0.1:8080`。
- 数据库：MySQL 8，Flyway 已执行到 v010。
- 服务：`ride-dispatch`、Nginx、MySQL 均为 active，Actuator health 为 `UP`。

## 冒烟结果

`pnpm run smoke:deployed`：7/7 PASS。

覆盖：Nginx 品牌代理、管理员/司机登录、司机列表、司机账户、司机越权拒绝、公共订单幂等重放、乘客取消、司机 SSE `CONNECTED` 事件。

## 核心链路结果

`pnpm run core:deployed`：7/7 PASS。

覆盖：后台派单 → 司机待确认 → 接单 → 四阶段履约 → 最终金额 120000 分（前端应展示 ¥1200.00）→ 支付上下文 → 生产支付 Provider 边界 → 线下收款二次确认 → 订单完成 → 司机业务收入 → 可提现余额保护 → 支付异常登记/驳回。

生产 profile 不启用 `/api/v1/local/mock-payments/**`，该接口现在返回结构化 `404 NOT_FOUND`，不会再误报 `500 INTERNAL_ERROR`。因此线上核心脚本不伪造第三方支付回调，改走已有的线下收款确认路径；线下收入按规则计入业务收入但不增加可提现余额，提现申请得到 `INSUFFICIENT_AVAILABLE_BALANCE`，这也是本次回归的预期结果。

真实支付宝/微信下单、签名验签、异步回调、对账和银行打款仍需商户/金融机构配置后再验收。

`pnpm run sse:deployed`：3/3 PASS。司机 SSE 连接收到 `CONNECTED`，真实派单后收到匹配司机、attemptId、orderNo 的 `DRIVER_NEW_DISPATCH`，测试订单随后自动拒单并取消。

## 全仓回归

- Maven `verify`：36 tests，0 failures。
- 本次 Provider/对账模型加入后 Maven `verify`：47 tests，0 failures。
- Admin Web：`vue-tsc + vite build` PASS。
- Passenger H5：`vue-tsc + vite build` PASS。
- Android：该历史报告使用 JDK 21、`ANDROID_HOME=D:\dev_tool\Android\Sdk` 构建，`testDebugUnitTest assembleDebug` PASS；这不代表当前 Android Gate 基线。当前项目 compile/jvmTarget 与 Gate 已统一为 Temurin JDK 17；APK 指向公网 API。

Provider 前置模型已部署但不改变生产支付写入：生产注册表为空，真实渠道尚未配置；请求本地 Mock 回调仍得到结构化 `404 NOT_FOUND`。对账比较器当前只做只读差异分类，不自动改账。

## 网络结论

- 公网 80 已从开发机访问成功。
- 管理端 8088 的阿里云安全组规则已配置；当前 Wi-Fi/代理对非标准端口主动重置连接，换热点或 SSH 隧道 `127.0.0.1:18088` 可用。长期建议绑定域名并统一走 HTTPS 443。
