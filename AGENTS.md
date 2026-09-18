# BugNest 项目配置说明

## 项目概览

BugNest 是一个轻量级缺陷管理平台，当前处于开发阶段。项目使用 Spring Boot 提供 REST API，账户数据通过 JDBC 保存到 PostgreSQL；旧内存用户实现仅保留给测试和兼容代码。

## 项目文档读取规则

- 开始任何开发、排查或方案设计前，先执行 `rg --files docs` 扫描 `docs/`，再阅读与当前任务相关的全部文档；任务涉及用户、数据库或接口时，必须优先阅读对应设计文档。
- `docs/` 中已有结论视为项目设计依据。若文档已经回答了需求、字段含义、约束或实现规则，不要重复向用户提问；只有文档和现有代码都无法确定时才提问。
- 文档与代码或迁移脚本冲突时，以代码和已执行的数据库迁移反映当前事实，同时指出冲突；不要静默覆盖文档或修改已执行迁移。
- 新增或改变重要业务约定时，同步更新 `docs/`，并保持文档与代码、迁移脚本一致。

## 技术栈

- Java LTS（当前基线 Java 21；补丁版本跟随官方最新稳定版）
- Spring Boot 4.x（当前基线 4.1.1；升级时使用官方最新稳定且兼容的版本）
- Maven（使用 Maven Wrapper）
- Spring Web MVC
- 测试：Spring Boot Test / JUnit

### 技术选型与企业工程标准

- 技术选型优先采用官方当前最新稳定版、长期维护版本和企业生产实践；不得使用 alpha、beta、snapshot 或无人维护的依赖。
- “最新”必须以实施时的官方发布信息和兼容性验证为准，不得为了追版本号盲目升级；升级必须同步验证编译、测试、安全扫描和运行时兼容性。
- Maven 依赖优先通过 Spring Boot BOM 统一版本；直接声明版本时必须有明确原因，并定期检查 CVE、许可证和依赖生命周期。
- 浏览器端异步请求统一使用标准 Fetch API 或前端框架提供的现代 HTTP 客户端；禁止使用 XMLHttpRequest、jQuery AJAX 或以 AJAX 为基础的旧式封装。页面交互保持在当前注册页面，不因接口调用跳转 URL。
- 后端接口使用标准 HTTP、REST/JSON 和明确的状态码；业务逻辑不得依赖前端是否刷新或跳转页面。
- 新增前端技术栈前必须先确认项目确实需要；不为一个接口引入大型框架或额外运行时。
- 安全、密码、数据库和测试相关依赖必须使用官方维护或行业认可的实现，并在生产代码中保留可验证的自动化测试。

注意：README 中列出的 MySQL 和 MyBatis 尚未接入，当前数据库实现为 PostgreSQL + JDBC；除非需求明确，不引入 ORM 或替换现有数据访问方案。

## 目录结构

```text
src/main/java/com/nolla/bugnest/
├── controller/   REST 控制器
├── service/      业务逻辑
├── repository/   数据访问接口及内存实现
├── model/        领域模型（Account、旧 User）
├── dto/          请求 DTO
└── exception/    全局异常处理

src/main/resources/application.yaml  Spring Boot 配置
src/test/java/                       单元测试和上下文测试
```

## 关键配置

- 应用名称：`bugnest`
- 配置文件：`src/main/resources/application.yaml`
- 默认端口未覆盖，使用 Spring Boot 默认端口 `8080`
- 账户存储实现：`JdbcAccountRepository`
- 账户数据访问接口：`AccountRepository`
- 旧用户存储：`MemoryUserRepository`，不注册为生产 Bean

## Flyway 数据库迁移

- Flyway 负责管理 PostgreSQL 数据库结构，不负责 Repository 或业务数据。
- PostgreSQL 版本：18.6。
- 迁移文件目录：`src/main/resources/db/migration/`
- 文件命名格式：`V{版本}__{描述}.sql`，已执行的迁移文件不得修改，只能新增更高版本。
- 当前首个迁移：`V1__create_app_users.sql`。
- 本地数据库配置位于 `application-local.yaml`，数据库账号通过 `BUGNEST_DB_USER` 和 `BUGNEST_DB_PASSWORD` 环境变量提供。
- 现有手工创建的数据库首次接入 Flyway 前，需要单独执行一次 baseline；不要将 `baseline-on-migrate` 永久开启。
- 自动化数据库测试使用 Testcontainers，不连接开发机真实数据库。

## 常用命令

Windows：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
.\mvnw.cmd clean package
```

Linux/macOS：

```bash
./mvnw test
./mvnw spring-boot:run
./mvnw clean package
```

## 当前 API

- `GET /api/ping`：健康检查
- `GET /api/add?a=2&b=3`：简单加法示例
- `POST /auth/sign-up`：注册账户
- `POST /auth/sign-up/username-preview`：预览或检查用户名
- `PATCH /accounts/{id}/nickname`：修改昵称

## 开发约定

- 新增接口时沿用 `controller → service → repository` 的调用分层。
- 输入校验放在业务层；统一异常响应由 `GlobalExceptionHandler` 处理。
- 修改业务逻辑时同步补充或调整 `src/test/java` 下的测试。
- 不要在未明确需求前引入数据库、ORM 或新的依赖；先复用现有 Maven 和 Spring Boot 配置。
