# Repository Layout

> [中文](project-structure.md)

```
actionow/
├── actionow.sh                    # one-click management script
├── README.md                      # Chinese documentation
├── README_EN.md                   # English documentation
├── CONTRIBUTING.md                # contribution guide
├── assets/                        # brand assets and demo media
│   ├── logo.png
│   ├── full-logo.png
│   ├── full-logo-dark.png
│   └── demo/
│
├── docs/                          # detailed documentation
│   ├── architecture.md            # architecture overview
│   ├── configuration.md           # configuration and ports
│   ├── development.md             # local development and builds
│   └── project-structure.md       # repository layout (this file)
│
├── backend/                       # Spring Cloud backend
│   ├── pom.xml
│   ├── actionow-gateway/          # API gateway (8080)
│   ├── actionow-user/             # users and authentication (8081)
│   ├── actionow-workspace/        # workspaces (8082)
│   ├── actionow-wallet/           # credits wallet (8083)
│   ├── actionow-billing/          # payments and subscriptions (8092)
│   ├── actionow-project/          # business entities and versioning (8084)
│   ├── actionow-ai/               # model management and prompts (8086)
│   ├── actionow-task/             # async task scheduling (8087)
│   ├── actionow-collab/           # real-time collaboration (8088)
│   ├── actionow-system/           # system configuration (8089)
│   ├── actionow-canvas/           # canvas nodes and relations (8090)
│   ├── actionow-agent/            # agent service (8091)
│   ├── actionow-common/           # shared starters
│   └── scripts/
│
├── web/                           # Next.js 16 frontend
│   ├── package.json
│   ├── next.config.ts
│   ├── ecosystem.config.cjs
│   ├── public/
│   └── src/
│
└── docker/                        # unified orchestration
    ├── Dockerfile.backend.prod
    ├── Dockerfile.backend.dev
    ├── Dockerfile.web.prod
    ├── docker-compose.prod.yml
    ├── docker-compose.dev.yml
    ├── docker-compose.apps.yml
    ├── docker-compose.bundle.yml
    ├── docker-compose.web.yml
    ├── .env.example
    ├── init-db/
    ├── init-rabbitmq/
    └── scripts/
```
