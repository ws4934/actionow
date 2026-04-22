# 配置参考

> [English](configuration.en.md)

## 服务端配置（`docker/.env.prod`）

通过 `./actionow.sh init` 交互式生成。常用字段：

| 分类       | 变量                                                                                | 说明                                            |
|------------|-------------------------------------------------------------------------------------|-------------------------------------------------|
| 基础       | `TZ`、`SPRING_PROFILES_ACTIVE`                                                      | 时区、Spring Profile                            |
| 数据库     | `DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`、`DB_NAME`                           | PostgreSQL 16 加 pgvector                       |
| 缓存       | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`                                        | Redis 7                                         |
| 消息       | `RABBITMQ_*`、`RABBITMQ_SSL_ENABLED`                                                | RabbitMQ，外部连接可启用 SSL                    |
| 对象存储   | `OSS_TYPE`（`minio` / `s3` / `aliyun` / `r2` / `tos`）加对应凭证                    | 多云对象存储抽象                                |
| 鉴权       | `JWT_SECRET`、`INTERNAL_AUTH_SECRET`                                                | 生产必须替换为不少于 32 位随机字符串            |
| LLM        | `OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`GEMINI_API_KEY`                             | 按需配置，缺失则跳过对应 Provider               |
| 邮件       | `MAIL_PROVIDER`（`resend` / `smtp` / `cloudflare`）加对应凭证                       | 多 Provider 邮件网关，支持运行时热切换          |
| 支付       | `WECHAT_*`                                                                          | 微信支付（可选）                                |
| 前端       | `WEB_PORT`、`WEB_API_BASE_URL`、`WEB_NEXT_PUBLIC_WS_URL`、`WEB_NEXT_PUBLIC_SITE_URL`| 容器内前端运行参数                              |
| 资源限制   | `*_MEMORY`（如 `AGENT_MEMORY=2G`）                                                  | 按机器规格调整                                  |

完整字段见 [`docker/.env.example`](../docker/.env.example)。

## 前端本地配置（`web/.env.local`）

```dotenv
API_BASE_URL=http://127.0.0.1:8080
NEXT_PUBLIC_WS_URL=ws://127.0.0.1:8080/ws
NEXT_PUBLIC_SITE_URL=http://localhost:3000
```

## 端口分配

| 服务                | 端口 | 控制台                                |
|---------------------|------|---------------------------------------|
| Web 前端            | 3000 | http://localhost:3000                 |
| actionow-gateway    | 8080 | http://localhost:8080/doc.html        |
| actionow-user       | 8081 |                                       |
| actionow-workspace  | 8082 |                                       |
| actionow-wallet     | 8083 |                                       |
| actionow-project    | 8084 |                                       |
| actionow-ai         | 8086 |                                       |
| actionow-task       | 8087 |                                       |
| actionow-collab     | 8088 |                                       |
| actionow-system     | 8089 |                                       |
| actionow-canvas     | 8090 |                                       |
| actionow-agent      | 8091 | http://localhost:8091/swagger-ui.html |
| actionow-billing    | 8092 |                                       |
| PostgreSQL          | 5432 |                                       |
| Redis               | 6379 |                                       |
| RabbitMQ            | 5672 | http://localhost:15672                |
| MinIO               | 9000 | http://localhost:9001                 |
