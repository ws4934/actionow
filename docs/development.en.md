# Development & Build

> [中文](development.md)

## Prerequisites

| Component | Version                                                              |
|-----------|----------------------------------------------------------------------|
| Docker    | 24+ with Docker Compose v2                                           |
| Node.js   | 20+ (only for local frontend development)                            |
| JDK       | 21+ (only for local backend development)                             |
| Maven     | 3.9+ (the repo ships `./mvnw`)                                       |
| Memory    | 8 GB minimum, 16 GB+ recommended for a full deployment               |

## Local development

Run the backend from your IDE, the frontend in hot-reload mode, and infrastructure in containers:

```bash
COMPOSE_MODE=dev ./actionow.sh infra      # start PostgreSQL / Redis / RabbitMQ / MinIO
./actionow.sh web local-dev               # frontend npm run dev
# Run each backend microservice from your IDE with the dev profile.
# VM options: --enable-preview -Dspring.profiles.active=dev
```

## Deployment modes

| Mode             | How to trigger                              | Use case                                                                  |
|------------------|---------------------------------------------|---------------------------------------------------------------------------|
| `prod` (default) | `./actionow.sh up`                          | In-image build; production-grade full deployment.                         |
| `dev`            | `COMPOSE_MODE=dev ./actionow.sh up`         | Build the JAR locally, mount it into the container; faster iteration.     |
| `apps`           | `COMPOSE_MODE=apps ./actionow.sh up`        | Application containers only; connect to external RDS, ElastiCache, CloudAMQP, S3. |

## Command reference

```bash
# Full stack
./actionow.sh up | down | restart | status | health | clean
./actionow.sh logs [-f] [service]

# Backend
./actionow.sh backend rebuild agent
./actionow.sh backend up gateway user
./actionow.sh backend reset-db

# Frontend
./actionow.sh web build | up | down
./actionow.sh web logs -f
./actionow.sh web local-dev

# Advanced
./docker/scripts/deploy.sh help
./docker/scripts/health-check.sh
./docker/scripts/build-bundle.sh          # build an offline image tarball
```

## Frontend against a remote backend

```bash
cd web
cp .env.example .env.local
# Edit:
#   API_BASE_URL=https://api.your-domain.com
#   NEXT_PUBLIC_WS_URL=wss://api.your-domain.com/ws
#   NEXT_PUBLIC_SITE_URL=http://localhost:3000
npm install
npm run dev
```

## Cloudflare Workers

```bash
cd web
npm run deploy            # deploy to the bound Worker via @opennextjs/cloudflare
```

## Build & Test

```bash
# Backend
cd backend
./mvnw clean install -DskipTests             # full build
./mvnw -pl actionow-agent -am clean package  # single module
./mvnw test                                   # unit tests
./mvnw verify -Pintegration                   # integration tests (Testcontainers)

# Frontend
cd web
npm install
npm run lint
npm run build                 # outputs .next/standalone
npm run build:pm2             # plus prepare-standalone.cjs for PM2

# API documentation aggregation
cd backend
python scripts/generate_module_api_docs.py
```
