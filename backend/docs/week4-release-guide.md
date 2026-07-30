# 第四周 Vue 联调与发布验收

## 接入范围

- Vite `/api` 开发代理和 Nginx 生产同源代理。
- 原生 `fetch` 请求层统一处理 JSON、JWT、401、10 秒超时和取消。
- 登录保存 JWT 到 `sessionStorage`，`/workspace` 使用路由守卫。
- 工作台提交异步诊断并按后端 `pollAfterMs` 轮询；重复提交或卸载会取消旧请求。
- 后端步骤、证据、SOP 和客服回复直接映射到页面；Canvas 只保留视觉效果。
- 当前用户历史、报告回看、Apply 和 Discard 已接入。
- 首页在已登录时由真实历史计算完成率、成功率和活动队列；未登录显示占位值。

## 本地运行验证

后端必须使用 JDK 21：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -s .mvn/settings.xml test
docker compose up -d mysql
mvn -s .mvn/settings.xml spring-boot:run
```

前端：

```powershell
cd ..\frontend
npm ci
npm run dev
npm run build
```

依次验证登录、创建诊断、终态停止轮询、历史回看、Apply 和 Discard。演示账号为 `demo` / `SupportOps@2026`。

## 发布检查

1. `.env` 已被 Git 忽略，只提交 `.env.example`。
2. 替换 JWT Secret、数据库密码和演示账号密码。
3. Mock 发布保持 `AI_MODE=mock`，不需要 API Key。
4. 真实模式设置 `AI_MODE=real` 和千问配置，禁止记录模型请求/响应正文。
5. `docker compose config`、Maven 测试、Vue 构建和 HTTP 冒烟链路全部通过后再发布。

## 模型用量保护

- 单账号默认每分钟最多创建 5 个新诊断，同时最多运行 2 个诊断。
- 单任务最多调用模型 2 次，描述最多 2000 字，模型输出最多 600 Token。
- 用户每日额度由 `support_users.daily_quota` 控制。
- 供应商返回额度/余额耗尽后开启实例级熔断，后续任务直接使用规则引擎和安全模板；补充额度后重启后端可恢复模型调用。
