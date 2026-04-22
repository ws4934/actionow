# 工程结构

> [English](project-structure.en.md)

```
actionow/
├── actionow.sh                    # 一键管理脚本
├── README.md                      # 中文文档
├── README_EN.md                   # 英文文档
├── CONTRIBUTING.md                # 贡献指南
├── assets/                        # 品牌资源与演示素材
│   ├── logo.png
│   ├── full-logo.png
│   ├── full-logo-dark.png
│   └── demo/
│
├── docs/                          # 详细文档
│   ├── architecture.md            # 架构总览
│   ├── configuration.md           # 配置与端口
│   ├── development.md             # 本地开发与构建
│   └── project-structure.md       # 工程结构（本文）
│
├── backend/                       # Spring Cloud 后端
│   ├── pom.xml
│   ├── actionow-gateway/          # API 网关（8080）
│   ├── actionow-user/             # 用户与认证（8081）
│   ├── actionow-workspace/        # 工作空间（8082）
│   ├── actionow-wallet/           # 积分钱包（8083）
│   ├── actionow-billing/          # 支付与订阅（8092）
│   ├── actionow-project/          # 业务实体与版本（8084）
│   ├── actionow-ai/               # 模型管理与提示词（8086）
│   ├── actionow-task/             # 异步任务调度（8087）
│   ├── actionow-collab/           # 实时协作（8088）
│   ├── actionow-system/           # 系统配置（8089）
│   ├── actionow-canvas/           # 画布节点与关系（8090）
│   ├── actionow-agent/            # 智能体服务（8091）
│   ├── actionow-common/           # 公共 starter
│   └── scripts/
│
├── web/                           # Next.js 16 前端
│   ├── package.json
│   ├── next.config.ts
│   ├── ecosystem.config.cjs
│   ├── public/
│   └── src/
│
└── docker/                        # 统一编排
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
