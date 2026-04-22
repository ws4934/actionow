# Configuration

> [中文](configuration.md)

## Server-side (`docker/.env.prod`)

Generated interactively by `./actionow.sh init`. Common fields:

| Group         | Variables                                                                              | Notes                                            |
|---------------|----------------------------------------------------------------------------------------|--------------------------------------------------|
| Basics        | `TZ`, `SPRING_PROFILES_ACTIVE`                                                         | Timezone and Spring profile.                     |
| Database      | `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`                              | PostgreSQL 16 with pgvector.                     |
| Cache         | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`                                           | Redis 7.                                         |
| Messaging     | `RABBITMQ_*`, `RABBITMQ_SSL_ENABLED`                                                   | RabbitMQ; SSL recommended for external brokers.  |
| Object store  | `OSS_TYPE` (`minio` / `s3` / `aliyun` / `r2` / `tos`) plus matching credentials        | Multi-cloud object-storage abstraction.          |
| Auth          | `JWT_SECRET`, `INTERNAL_AUTH_SECRET`                                                   | Replace with 32+ random characters in production.|
| LLM           | `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`                                | Configure as needed; missing providers are skipped. |
| Mail          | `MAIL_PROVIDER` (`resend` / `smtp` / `cloudflare`) plus matching credentials           | Multi-provider mail gateway with runtime hot-swap.|
| Payments      | `WECHAT_*`                                                                             | WeChat Pay (optional).                           |
| Frontend      | `WEB_PORT`, `WEB_API_BASE_URL`, `WEB_NEXT_PUBLIC_WS_URL`, `WEB_NEXT_PUBLIC_SITE_URL`   | Runtime parameters for the in-container frontend.|
| Resource caps | `*_MEMORY` (e.g. `AGENT_MEMORY=2G`)                                                    | Tune to match the host.                          |

See [`docker/.env.example`](../docker/.env.example) for the full list.

## Frontend local config (`web/.env.local`)

```dotenv
API_BASE_URL=http://127.0.0.1:8080
NEXT_PUBLIC_WS_URL=ws://127.0.0.1:8080/ws
NEXT_PUBLIC_SITE_URL=http://localhost:3000
```

## Port reference

| Service             | Port | Console                              |
|---------------------|------|---------------------------------------|
| Web frontend        | 3000 | http://localhost:3000                 |
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
