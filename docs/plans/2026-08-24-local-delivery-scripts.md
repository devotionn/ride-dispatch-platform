# 本地可重复交付脚本计划

## 目标

把后端构建、启动、停止和 HTTP 深度冒烟收敛成可重复的 PowerShell 入口，方便本机联调，也方便明天迁移到云服务器时复用。

## 约定

- 后端本地 profile 使用 H2，默认端口 `8081`。
- 构建必须使用 Java 21；默认执行 `mvn verify`，保留 `-SkipTests` 仅用于快速打包。
- 启动脚本只操作项目自己的 Spring Boot jar，不自动停止其他 Java 进程。
- 冒烟脚本依赖后端刚重启的空 H2 库，因为深度脚本会校验精确的账本余额和条数。

## 入口

1. `build-local.ps1`：检查 Java/Maven，执行 `verify` 或快速 `package`。
2. `launch-local.ps1`：检查端口和 jar，启动 `local` profile，等待健康检查并写入带时间戳日志。
3. `stop-local.ps1`：按项目 jar 的精确路径停止后端，不影响其他 Java 服务。
4. `smoke-local.ps1`：健康检查后运行 `e2e/local-http-depth.cjs`。
