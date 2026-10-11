# BugNest 提测单

## 1. 本次开发内容

- 本次提测范围：`app_users` 表结构迁移、JdbcAccountRepository 和 PostgreSQL 持久化。
- 通过 Flyway V2 将已存在的 `username` 列迁移为 `nickname`。
- 使用 `JdbcTemplate` 实现账户插入、查询、唯一性检查和昵称更新。
- 使用 Testcontainers PostgreSQL 18.6 验证数据库集成行为。

## 2. 需求 / Issue

- 需求：将正式账户注册链路接入 PostgreSQL，并保证已执行的 V1 迁移不被修改。
- Issue 编号：未提供，待补充。

## 3. 改动范围

- 新增 V2 Flyway migration。
- 新增 `AccountRepository` 和 `JdbcAccountRepository`。
- 新增 PostgreSQL Repository 集成测试和 Flyway 集成测试。
- 保留 JDBC 分层，不引入 ORM。

## 4. 接口变化

| 方法 | 路径 | 请求 | 成功响应 | 失败响应 |
|---|---|---|---|---|
| POST | `/auth/sign-up` | 账户注册请求 | `201 Created`：返回 `userId` | `409 Conflict`：邮箱或用户名唯一约束冲突；数据库连接或迁移失败时应用基础设施异常 |
| POST | `/auth/sign-up/username-preview` | 昵称和可选用户名 | `200 OK`：返回用户名及可用状态 | 数据库查询失败时基础设施异常未统一定义 |
| PATCH | `/accounts/{id}/nickname` | 新昵称 | `204 No Content` | `404 Not Found`：账户不存在；数据库更新失败时基础设施异常未统一定义 |

## 5. 数据库变化

- 数据库：PostgreSQL，应用默认连接 `jdbc:postgresql://localhost:5432/bugnest`。
- Migration：V1 创建 `app_users`；V2 重命名 `username` 为 `nickname`、回填空值并设置非空约束。
- 表/字段：最终字段为 `user_id`、`user_name`、`nickname`、`email`、`password_hash`、`created_at`。
- 约束/索引：主键、用户名唯一约束、用户名格式检查、昵称非空检查、邮箱大小写不敏感唯一索引。
- 运行时使用情况：`JdbcAccountRepository.save` 使用 `INSERT ... RETURNING`；查询使用 `findById`、`findByEmail` 和 `existsByUserName`；昵称使用 `UPDATE ... RETURNING`。

## 6. 正常流程

- [ ] 使用 Flyway 启动应用后，`public.app_users` 存在 6 个最终字段。
- [ ] 验证 `flyway_schema_history` 中 V1 和 V2 执行成功。
- [ ] 插入账户后能够通过 ID 查询到账户完整字段。
- [ ] 通过邮箱查询到账户。
- [ ] 修改昵称成功，且 `user_name`、邮箱和密码哈希保持不变。
- [ ] 重启应用时 Flyway 校验通过，不重复执行 V2。

## 7. 异常流程

- [ ] 验证旧字段 `username` 不再存在。
- [ ] 插入重复 `user_name` 时返回账户冲突，不产生第二条记录。
- [ ] 插入重复邮箱时命中大小写不敏感唯一索引并返回账户冲突。
- [ ] 迁移前存在空昵称时，V2 将其回填为对应的 `user_name`。
- [ ] PostgreSQL 不可连接或迁移失败时，应用启动失败或接口返回基础设施错误，不得静默使用内存数据。

## 8. 测试环境

| 项目 | 内容 |
|---|---|
| 地址 | 未提供，待补充；默认数据库地址为 `localhost:5432` |
| 分支 | `main` |
| 提交/版本 | `ba05023`；数据库迁移提交 `58503bb` |
| 启动方式 | `mvn spring-boot:run` 或 `./mvnw spring-boot:run` |
| 应用运行时 | Java 21、Spring Boot 4.1.1、Flyway |
| 数据库 | 本地 PostgreSQL；自动化测试使用 `postgres:18.6-alpine` Testcontainers |
| 测试命令 | `mvn test` 或 `./mvnw test` |

## 9. 测试数据

- 数据库账号通过 `BUGNEST_DB_USER` 和 `BUGNEST_DB_PASSWORD` 提供。
- 默认数据库：`bugnest`；默认 JDBC URL：`jdbc:postgresql://localhost:5432/bugnest`。
- 正常账户：`user_name=zhang_san`、`email=zhang@example.com`、`nickname=张三`、`password_hash=argon2-hash`。
- 重复用户名：再次插入 `zhang_san`。
- 重复邮箱：使用 `ZHANG@example.com` 验证邮箱大小写不敏感约束。
- 昵称更新：将 `张三` 更新为 `李四`。

## 10. 风险点

- V2 依赖 V1 已存在且 Flyway 历史一致，不能直接修改已执行的 V1。
- 本地首次接入手工数据库时需要单独 baseline，当前配置未开启 `baseline-on-migrate`。
- Testcontainers 测试依赖 Docker 服务和 `postgres:18.6-alpine` 镜像。
- 数据库唯一约束是并发场景下的最终保证，应用层查询只用于提前反馈。

## 11. 已知问题

- 当前执行环境未发现 Docker，因此 Flyway 和 JDBC Repository 集成测试报错，尚未完成真实 PostgreSQL 验证。
- 生产数据库地址、分支部署地址和测试账号未提供，待测试环境补充。
- Repository 将数据库完整性异常统一转换为账户冲突的范围较宽，后续可细分约束错误类型。

## 12. 验收标准

1. [ ] 在 PostgreSQL 18.6 上可成功执行 V1+V2。
2. [ ] V2 执行后存在 `nickname`，不存在旧字段 `username`。
3. [ ] 空昵称能够按规则回填，且最终 `nickname` 为非空。
4. [ ] JDBC Repository 能完成账户插入、按 ID 查询、按邮箱查询、用户名存在性检查和昵称更新。
5. [ ] 重复用户名和重复邮箱受到数据库约束保护并返回 `409`。
6. [ ] Docker 可用后，Flyway 和 JDBC Repository Testcontainers 测试全部通过。
