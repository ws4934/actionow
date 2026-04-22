# 本地开发与构建

> [English](development.en.md)

## 环境要求

| 组件     | 版本                                                   |
|----------|--------------------------------------------------------|
| Docker   | 24 及以上，包含 Docker Compose v2                       |
| Node.js  | 20 及以上（仅本地前端开发需要）                         |
| JDK      | 21 及以上（仅本地后端开发需要）                         |
| Maven    | 3.9 及以上（仓库附带 `./mvnw`）                         |
| 内存     | 至少 8 GB，完整部署建议 16 GB 以上                      |

## 本地开发

后端在 IDE 中调试，前端使用热更，基础设施容器化：

```bash
COMPOSE_MODE=dev ./actionow.sh infra      # 启动 PostgreSQL / Redis / RabbitMQ / MinIO
./actionow.sh web local-dev               # 前端 npm run dev
# 在 IDE 中以 dev profile 启动各微服务
# VM options: --enable-preview -Dspring.profiles.active=dev
```

## 部署模式

| 模式             | 触发方式                              | 适用场景                                                          |
|------------------|---------------------------------------|-------------------------------------------------------------------|
| `prod`（默认）   | `./actionow.sh up`                    | 镜像内构建 JAR，生产级完整部署                                    |
| `dev`            | `COMPOSE_MODE=dev ./actionow.sh up`   | 本地构建 JAR，挂载到容器，迭代更快                                |
| `apps`           | `COMPOSE_MODE=apps ./actionow.sh up`  | 仅部署应用容器，对接外部 RDS、ElastiCache、CloudAMQP、S3 等        |

## 命令参考

```bash
# 全栈
./actionow.sh up | down | restart | status | health | clean
./actionow.sh logs [-f] [service]

# 后端
./actionow.sh backend rebuild agent
./actionow.sh backend up gateway user
./actionow.sh backend reset-db

# 前端
./actionow.sh web build | up | down
./actionow.sh web logs -f
./actionow.sh web local-dev

# 高级
./docker/scripts/deploy.sh help
./docker/scripts/health-check.sh
./docker/scripts/build-bundle.sh          # 构建离线镜像 tarball
```

## 仅前端对接远程后端

```bash
cd web
cp .env.example .env.local
# 编辑：
#   API_BASE_URL=https://api.your-domain.com
#   NEXT_PUBLIC_WS_URL=wss://api.your-domain.com/ws
#   NEXT_PUBLIC_SITE_URL=http://localhost:3000
npm install
npm run dev
```

## Cloudflare Workers 部署

```bash
cd web
npm run deploy            # 通过 @opennextjs/cloudflare 推送至 Worker
```

## 构建与测试

```bash
# 后端
cd backend
./mvnw clean install -DskipTests             # 全量构建
./mvnw -pl actionow-agent -am clean package  # 单模块构建
./mvnw test                                   # 单元测试
./mvnw verify -Pintegration                   # 集成测试（Testcontainers）

# 前端
cd web
npm install
npm run lint
npm run build                 # 输出 .next/standalone
npm run build:pm2             # 加 prepare-standalone.cjs，供 PM2 运行

# API 文档聚合
cd backend
python scripts/generate_module_api_docs.py
```
