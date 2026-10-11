# BugNest

轻量缺陷管理平台，当前处于开发阶段。

## 当前功能

- 前端工作台：状态概览、解决进度、优先级分布、缺陷搜索与筛选、列表和看板、新建与编辑。
- 账户：注册、用户名预览、邮箱或用户名登录、刷新后恢复会话、修改昵称、安全退出。
- **缺陷管理目前使用内存演示数据，刷新后恢复示例，尚未实现后端持久化和团队协作。账户功能连接真实 API。**

## 技术栈

- Java 21、Spring Boot 4.1.1、Maven Wrapper。
- PostgreSQL + JDBC + Flyway；Spring Security；Spring Session Redis。
- Vue 3 + TypeScript + Vite；Vue Router；Element Plus。
- Fetch API、服务器 Session Cookie、CSRF；不在浏览器存储会话凭据。

## 开发运行

需要 Java 21、Node.js 22.12+（建议使用受维护的 LTS）、PostgreSQL 和 Redis。

在仓库根目录启动后端，按现有配置准备 `BUGNEST_DB_USER`、`BUGNEST_DB_PASSWORD`、`BUGNEST_RATE_LIMIT_HMAC_KEY`（至少 32 字节），以及数据库、Redis 的连接配置。不要把凭据提交到仓库。

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

使用 IntelliJ IDEA 时，选择项目共享运行配置 **BugNest Local**（`.run/BugNest Local.run.xml`）。该配置启用 `local` Profile，并以仓库根目录为工作目录，读取 `application-local.yaml` 和根目录 `.env`。

若沿用 IDEA 自动生成的 `BugnestApplication` 配置，在 `Run → Edit Configurations → Modify options → Spring → Active profiles` 填写 `local`。未启用该 Profile 时，默认配置不包含数据库 URL，会出现 `Failed to determine a suitable driver class`。运行配置不保存数据库凭据。

另一个终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

访问 **https://localhost:5173**。开发证书由 Vite 官方 `plugin-basic-ssl` 在本地生成；首次访问需要浏览器允许该本地自签名证书。开发代理将 `/auth`、`/accounts`、`/api` 转发到 `http://localhost:8080`。HTTPS 保持现有 `Secure` 会话 Cookie 的安全约定。

仅预览界面时可以只启动前端；示例缺陷可操作，账户功能需要后端、PostgreSQL 和 Redis。

## 构建与发布

Windows 打包前先停止 Vite 开发服务器（终端按 Ctrl+C），避免 `npm ci` 替换正在使用的原生构建模块时遇到文件锁。

```powershell
.\mvnw.cmd clean package
```

Maven 在打包阶段自动执行 `npm ci` 和 `npm run build`，将 `frontend/dist` 复制到 JAR 的 `static/`，前后端由同一 Spring Boot 应用提供，无需另行部署静态网站。构建机需要 Node.js 和 npm。线上使用 HTTPS 入口；开发自签名证书不用于生产。

生产支持 `/`、`/issues`、`/settings`、`/login`、`/register` 直接访问和刷新。仅显式允许前端 GET 路由和资源匿名读取，API 保持原有认证与 CSRF 防护。

```powershell
java -jar target/bugnest-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

以上命令使用本地配置；生产环境需提供独立数据库、Redis、密钥与 HTTPS 配置。

## 验证

```powershell
cd frontend
npm test
npm run build
npm audit
```

在仓库根目录运行：

```powershell
.\mvnw.cmd test
```

完整数据库与 Redis 集成测试需要已运行的 Docker，使用 Testcontainers，不连接开发机数据库。

测试源码（`src/test/`、`frontend/tests/`）随代码保存在 `main`。提测文档仅保存在 [`codex/test-docs` 分支的 `提测文档/` 目录](https://github.com/Insecshin/BugNest/tree/codex/test-docs/提测文档)，`main` 不保存该目录。

测试源码和提测文档不通过 `.gitignore` 隐藏。`.gitignore` 排除本地环境凭据、私钥与密钥库、运行日志、内存转储和既有构建/编辑器产物；`.env.example` 配置模板保留。已被 Git 跟踪的文件不会因新增忽略规则而自动停止上传。

无 Docker 时可单独验证其余测试（不代表完整集成测试通过）：

```powershell
.\mvnw.cmd '-Dtest=!LoginSecurityIntegrationTest,!FlywayPostgresIntegrationTest,!JdbcAccountRepositoryIntegrationTest' test
```

详细实现说明见 [前端界面设计](docs/Plans/前端界面设计.md)，接口见 [接口文档](docs/接口文档.md)。
