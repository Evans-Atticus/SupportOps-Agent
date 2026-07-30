# SupportOps Agent Vue

Vue 3 + Vite 的 SupportOps 工单诊断前端，已接入独立 Spring Boot 后端。登录令牌保存在 `sessionStorage`；工作台使用真实异步诊断、800ms 轮询、历史记录、Apply 和 Discard 接口。

## 本地联调

1. 在 monorepo 的 `backend` 目录启动 MySQL 和 Spring Boot（默认端口 8080）。
2. 在本目录执行 `npm ci` 和 `npm run dev`。
3. 打开 `http://localhost:4173`，使用本地演示账号登录；默认管理员为 `admin` / `12345678l`。

Vite 默认将 `/api` 代理到 `http://localhost:8080`，可用环境变量 `VITE_BACKEND_TARGET` 覆盖。浏览器直接访问远端 API 时可设置 `VITE_API_BASE_URL`。

## 一键容器发布

在 monorepo 根目录复制 `.env.example` 为 `.env`，替换示例密码和 JWT Secret。AI Key 只通过根目录 `.env` 注入后端容器，不进入 Vue 工程或前端构建上下文：

```powershell
Set-Location ..
Copy-Item .env.example .env
docker compose up -d --build
```

访问 `http://localhost:8088`。默认使用 Mock AI，不需要外部 API Key；真实千问模式需要设置 `AI_MODE=real` 和对应 AI 环境变量。

## 验证

```powershell
npm run build
```

后端在 `backend` 目录使用 JDK 21 执行 `mvn -s .mvn/settings.xml test`。发布前应扫描 `.env`、Token、API Key、客户数据及本机绝对路径，且不得提交这些内容。
# 产品溯源中心

登录后访问 `http://localhost:4173/trace`。首页顶部的“产品溯源”也会进入该受保护页面。

正式 Vue 页面已接入 `/api/v1/trace`，包括溯源总览、产品档案、供应商与采购、生产批次、质量检验、仓储库存、物流运输、销售流向、售后工单、风险召回和溯源查询。

仓储库存页面支持“新增入库单”。保存后后端返回入库单号和不可枚举的溯源码，页面会立即刷新并可按新入库单号查询。智能诊断入口只携带工单号、业务主键和问题描述进入工作台，业务事实由后端重新取证。
