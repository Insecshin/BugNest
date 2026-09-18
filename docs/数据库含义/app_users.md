# app_users 表字段含义

数据库表：`public.app_users`

| 字段 | 含义 |
| --- | --- |
| `user_id` | 用户 ID |
| `user_name` | 唯一用户名，如同推特的 `@用户名` |
| `nickname` | 用户昵称，不唯一；由后续 Flyway 迁移将当前 `username` 重命名而来 |
| `email` | 注册邮箱 |
| `password_hash` | 密码 hash，不保存明文密码 |
| `created_at` | 用户创建时间戳 |

## 约束说明

- `user_id` 为主键，由数据库自动生成。
- `user_name` 必须唯一，只允许小写字母、数字和下划线。
- `user_name` 创建后不可修改；昵称修改不会同步修改 `user_name`。
- `nickname` 必填，保存用户原始文字，不保存拉丁化结果。
- `email` 使用忽略大小写的唯一索引。
- `password_hash` 只保存密码 hash，不允许保存明文密码。
- `created_at` 由数据库默认写入当前时间。

> 当前已执行的 `V1__create_app_users.sql` 仍使用 `username` 列。不得修改已执行迁移，后续通过新的迁移将其重命名为 `nickname`。
