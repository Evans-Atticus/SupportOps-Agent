# SupportOps Agent

SupportOps Agent 是一个面向电商售后场景的全栈智能客服与工单诊断项目。系统使用 Vue 3 提供客户、客服和管理员门户，使用 Spring Boot 完成受控业务查询、产品知识检索、确定性诊断、证据审计以及大模型理解与回复。

## Monorepo 结构

```text
SupportOps-Agent/
├─ backend/                 # Java 21 + Spring Boot + MySQL + LangChain4j
├─ frontend/                # Vue 3 + Vite + Nginx
├─ docker-compose.yml       # 全栈本地演示入口
└─ .env.example             # 本地环境变量模板
```

详细说明：

- [后端设计、场景与 API](backend/README.md)
- [前端运行说明](frontend/README.md)
- [Prompt 设计](backend/docs/prompt-design.md)
- [RAG 检索策略](backend/docs/rag-strategy.md)

## 一键启动

环境要求：Docker Desktop 或 Docker Engine（含 Compose）。

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

打开 <http://localhost:8088>。默认使用 Mock AI，不需要外部模型密钥。

本地演示账户：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `12345678l` |
| 客服人员 | `support01` | `12345678l` |
| 客户 | `customer01` | `12345678l` |

> 上述账户和密码只用于本地演示。不要将默认凭据用于公网部署；部署前必须替换数据库密码、JWT Secret、模型 Key 和外部系统 Token，并关闭或替换演示账户。

停止服务：

```powershell
docker compose down
```

如需同时删除本地演示数据库卷，请在确认数据无需保留后执行 `docker compose down -v`。

## 本地开发

后端：

```powershell
Set-Location backend
Copy-Item .env.example .env
mvn spring-boot:run
```

前端：

```powershell
Set-Location frontend
npm ci
npm run dev
```

前端默认运行于 <http://localhost:4173>，并将 `/api` 代理到 <http://localhost:8080>。

## 验证

后端需要 JDK 21，前端建议使用 Node.js 20。

```powershell
Set-Location backend
mvn test

Set-Location ..\frontend
npm ci
npm run build
```

GitHub Actions 会在推送到 `main` 或创建 Pull Request 时自动运行后端测试、前端依赖审计和生产构建。

## 开源发布前检查

- 不提交 `.env`、真实 API Key、Token、JWT Secret、数据库备份、日志或真实客户数据。
- 确认 Git 提交邮箱可以公开；如需隐藏个人邮箱，请使用 GitHub 提供的 `noreply` 邮箱。
- 添加明确的开源许可证。未添加许可证时，公开仓库不等于获得开源使用授权。
- 核实图片、字体、文档和第三方代码均具有可再分发权利。
- GitHub 仓库启用 Secret scanning、Push protection、Dependabot alerts 和分支保护。

## 许可证

本项目采用 [MIT License](LICENSE)。
