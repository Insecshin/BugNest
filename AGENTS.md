# BugNest 项目配置说明

## 项目概览

BugNest 是一个轻量级缺陷管理平台，当前处于开发阶段。项目使用 Spring Boot 提供 REST API，用户数据暂时保存在内存中。

## 技术栈

- Java 21
- Spring Boot 4.1.1
- Maven（使用 Maven Wrapper）
- Spring Web MVC
- 测试：Spring Boot Test / JUnit

注意：README 中列出的 MySQL 和 MyBatis 尚未接入，当前没有数据库连接配置。

## 目录结构

```text
src/main/java/com/nolla/bugnest/
├── controller/   REST 控制器
├── service/      业务逻辑
├── repository/   数据访问接口及内存实现
├── model/        领域模型
├── dto/          请求 DTO
└── exception/    全局异常处理

src/main/resources/application.yaml  Spring Boot 配置
src/test/java/                       单元测试和上下文测试
```

## 关键配置

- 应用名称：`bugnest`
- 配置文件：`src/main/resources/application.yaml`
- 默认端口未覆盖，使用 Spring Boot 默认端口 `8080`
- 用户存储实现：`MemoryUserRepository`
- 数据访问接口：`UserRepository`

## Flyway 数据库迁移

- Flyway 负责管理 PostgreSQL 数据库结构，不负责 Repository 或业务数据。
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
- `POST /users`：创建用户，请求体为 `{ "username": "Noah" }`
- `GET /users`：查询全部用户
- `GET /users/{id}`：查询单个用户
- `PUT /users/{id}`：更新用户名
- `DELETE /users/{id}`：删除用户

## 开发约定

- 新增接口时沿用 `controller → service → repository` 的调用分层。
- 输入校验放在业务层；统一异常响应由 `GlobalExceptionHandler` 处理。
- 修改业务逻辑时同步补充或调整 `src/test/java` 下的测试。
- 不要在未明确需求前引入数据库、ORM 或新的依赖；先复用现有 Maven 和 Spring Boot 配置。
