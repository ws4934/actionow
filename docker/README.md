# Actionow Docker Deployment

This directory contains Docker configuration for deploying the Actionow platform.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Load Balancer / Nginx                         │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
┌────────────────────────────────┴────────────────────────────────────┐
│                    actionow-gateway (8080)                            │
│                   API Routing / Auth / Rate Limiting                 │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
    ┌──────────┬─────────┬───────┴────┬──────────┬──────────┐
    │          │         │            │          │          │
    ▼          ▼         ▼            ▼          ▼          ▼
┌──────┐  ┌──────┐  ┌──────┐    ┌──────┐  ┌──────┐  ┌──────┐
│ User │  │Worksp│  │Wallet│    │Project│ │  AI  │  │ Task │
│ 8081 │  │ 8082 │  │ 8083 │    │ 8084 │  │ 8086 │  │ 8087 │
└──────┘  └──────┘  └──────┘    └──────┘  └──────┘  └──────┘
    │          │         │            │          │          │
    ▼          ▼         ▼            ▼          ▼          ▼
┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌─────────────────────┐
│Collab│  │Canvas│  │System│  │Agent │  │    Infrastructure   │
│ 8088 │  │ 8090 │  │ 8089 │  │ 8091 │  │ PostgreSQL / Redis  │
└──────┘  └──────┘  └──────┘  └──────┘  │ RabbitMQ / Nacos    │
                                        │ MinIO / S3          │
                                        └─────────────────────┘
```

### Service Responsibilities

| Service | Responsibility |
|---------|----------------|
| **Project (8084)** | Business entity CRUD + version control + file upload (Script, Episode, Storyboard, Character, Scene, Prop, Style, Asset) |
| **Canvas (8090)** | Canvas layout + node positions + entity relations (edges) |
| **Task (8087)** | AI generation task orchestration, async job processing |
| **AI (8086)** | Model provider management, Groovy template execution |
| **Agent (8091)** | AI Agent conversation, Python ADK integration |

## Quick Start

```bash
# 1. Initialize configuration
./actionow.sh init

# 2. Edit .env file (optional)
vim .env

# 3. Start infrastructure for local development
./actionow.sh infra

# 4. Or start everything
./actionow.sh up
```

## Directory Structure

```
docker/
├── docker-compose.prod.yml # Production compose (server-side build)
├── docker-compose.dev.yml  # Development compose (volume mount)
├── Dockerfile.prod         # Multi-stage build (Maven + JRE)
├── Dockerfile.dev          # Development container (volume mount)
├── .env.example            # Environment template
├── .env                    # Development config (git-ignored)
├── .env.prod               # Production config (git-ignored)
├── README.md               # This documentation
├── actionow.sh              # Development management script
├── init-db/                # Database initialization scripts
│   ├── 00-init-entrypoint.sh
│   ├── 01-core-schema.sql
│   ├── 02-system-seed.sql
│   └── 03-agent-seed.sql
└── scripts/                # Deployment scripts
    ├── deploy.sh           # Production deployment
    ├── update.sh           # Service update
    └── health-check.sh     # Health monitoring
```

## Commands

### `./actionow.sh <command> [options]`

| Command | Description |
|---------|-------------|
| `infra` | Start infrastructure only (dev mode) |
| `up [services...]` | Start all or specific services |
| `down [services...]` | Stop all or specific services |
| `restart <service>` | Rebuild and restart a service |
| `build [services...]` | Build JAR files |
| `logs [service] [-f]` | View logs |
| `ps` | Show service status |
| `exec <service>` | Open shell in container |
| `health` | Check service health |
| `clean` | Remove all containers and volumes |
| `init` | Create .env from template |

### Examples

```bash
# Development workflow
./actionow.sh infra              # Start PostgreSQL, Redis, etc.
# Run services in IDE...

# Production-like deployment
./actionow.sh up                 # Start everything

# Start specific services
./actionow.sh up gateway user workspace

# Restart with rebuild
./actionow.sh restart user       # Rebuild JAR + restart container

# View logs
./actionow.sh logs user -f       # Follow user service logs

# Health check
./actionow.sh health             # Check all services
```

## Configuration

### Environment Variables

Copy `.env.example` to `.env` and customize:

```bash
cp .env.example .env
vim .env
```

Key configurations:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_PASSWORD` | actionow123 | Database password |
| `REDIS_PASSWORD` | redis123 | Redis password |
| `JWT_SECRET` | ... | JWT signing key (CHANGE IN PRODUCTION!) |
| `JVM_XMS` / `JVM_XMX` | 256m / 512m | JVM memory settings |

### Profiles

The compose file uses profiles to control which services start:

| Profile | Services |
|---------|----------|
| `infra` | postgres, redis, rabbitmq, nacos, minio |
| `services` | All application services |
| `full` | Everything |
| `<service>` | Individual service (e.g., `gateway`, `user`) |

## Service Ports

### Infrastructure Services

| Service | Port | Web UI | Credentials |
|---------|------|--------|-------------|
| PostgreSQL | 5432 | - | actionow / actionow123 |
| Redis | 6379 | - | redis123 |
| RabbitMQ | 5672 | http://localhost:15672 | actionow / actionow123 |
| Nacos | 8848 | http://localhost:8848/nacos | nacos / nacos |
| MinIO | 9000 | http://localhost:9001 | actionow / actionow123 |

### Application Services

| Service | Port | API Path | Description |
|---------|------|----------|-------------|
| Gateway | 8080 | /api/* | API gateway entry |
| User | 8081 | /api/user/** | User authentication |
| Workspace | 8082 | /api/workspace/** | Workspace management |
| Wallet | 8083 | /api/wallet/** | Credits wallet |
| Project | 8084 | /api/project/** | Business entity CRUD + file upload |
| AI | 8086 | /api/ai/** | AI model provider management |
| Task | 8087 | /api/task/** | AI task orchestration |
| Collab | 8088 | /api/collab/** | Collaboration features |
| System | 8089 | /api/system/** | System config |
| Canvas | 8090 | /api/canvas/** | Canvas layout, nodes & relations |
| Agent | 8091 | /api/agent/** | AI Agent conversation |

## Database Initialization

### Automatic Initialization

Database initialization runs automatically on first start via `00-init-entrypoint.sh`. The scripts are executed in order:

1. `01-core-schema.sql` - Core DDL: extensions, public schema, tenant schema functions, billing, auth security
2. `02-system-seed.sql` - System bootstrap data: tenant_system, presets, Groovy templates, model providers
3. `03-agent-seed.sql` - Agent bootstrap data: base agent config, skills

### Tenant Schema Tables (30 tables)

**Core Business Entities (8 tables):**
- `t_script` - Scripts with version control
- `t_episode` - Episodes with version control
- `t_storyboard` - Storyboards with version control
- `t_character` - Characters with version control
- `t_scene` - Scenes with version control
- `t_prop` - Props with version control
- `t_style` - Styles with version control
- `t_asset` - Assets with version control

**Version Tables (8 tables):**
- `t_script_version`, `t_episode_version`, `t_storyboard_version`
- `t_character_version`, `t_scene_version`, `t_prop_version`
- `t_style_version`, `t_asset_version`

**Canvas & Relations (3 tables):**
- `t_canvas_layout` - Canvas viewport state
- `t_canvas_node` - Node positions in canvas
- `t_entity_relation` - Entity relationships (edges between nodes)

**Other Tables (11 tables):**
- `t_asset_lineage` - AI generation lineage
- `t_prompt_template` - Prompt templates
- `t_tag`, `t_tag_relation` - Tags
- `t_script_permission` - Script permissions
- `t_async_task` - Async tasks
- `t_comment` - Comments
- `t_audit_log` - Audit logs
- `t_agent_conversation`, `t_agent_message`, `t_agent_task` - Agent features

### Clean Reset

To completely reset the database:

```bash
# Stop services and remove all volumes
./actionow.sh clean

# Restart - will reinitialize database
./actionow.sh up
```

### Connect to Database

```bash
# Via docker exec
docker exec -it actionow-postgres psql -U actionow -d actionow

# Check public tables
\dt

# Check tenant schema
\dn
SET search_path TO tenant_system, public;
\dt
```

## Production Deployment

### Overview

Production deployment supports:
- **Server-side build**: Clone from GitHub, build on server (no local Docker build required)
- **Flexible infrastructure**: Choose Docker or external services for PostgreSQL/Redis/RabbitMQ
- **One-click deployment**: Interactive script for configuration and deployment

### File Structure

```
docker/
├── docker-compose.prod.yml # Production compose file
├── docker-compose.dev.yml  # Development compose file
├── Dockerfile.prod         # Multi-stage build (Maven + JRE)
├── Dockerfile.dev          # Development container
├── .env.example            # Configuration template
├── .env.prod               # Production config (generated, git-ignored)
├── init-db/                # Database initialization scripts
└── scripts/
    ├── deploy.sh           # One-click deployment script
    ├── update.sh           # Service update script
    └── health-check.sh     # Health monitoring script
```

### Quick Start (Production)

```bash
# 1. Clone the repository on your server
git clone https://github.com/actionow-ai/actionow.git
cd actionow/docker

# 2. Run interactive setup
./scripts/deploy.sh init

# 3. Follow prompts to configure:
#    - PostgreSQL: Docker or external
#    - Redis: Docker or external
#    - RabbitMQ: Docker or external
#    - Object Storage: MinIO/S3/Aliyun/R2/TOS
#    - Security: JWT secret
#    - AI providers: API keys (optional)
#    - OAuth: GitHub/Google (optional)
#    - Mail: Resend/SMTP (optional)
```

### Deployment Commands

```bash
# Interactive setup
./scripts/deploy.sh init

# Start services
./scripts/deploy.sh up

# Stop services
./scripts/deploy.sh down

# Restart services
./scripts/deploy.sh restart

# Rebuild and restart
./scripts/deploy.sh rebuild

# View status
./scripts/deploy.sh status

# View logs
./scripts/deploy.sh logs [service] [-f]

# Update services (pull code + rebuild)
./scripts/update.sh --pull --all

# Health check
./scripts/health-check.sh

# Clean everything (WARNING: removes data!)
./scripts/deploy.sh clean
```

### Infrastructure Options

| Component | Docker | External Service |
|-----------|--------|------------------|
| **PostgreSQL** | pgvector/pgvector:pg16 | RDS, Cloud SQL, self-hosted |
| **Redis** | redis:7-alpine | ElastiCache, Memorystore |
| **RabbitMQ** | rabbitmq:3-management | CloudAMQP, AmazonMQ |
| **Nacos** | Always Docker | - |
| **Object Storage** | MinIO | S3, Aliyun OSS, R2, TOS |

### Configuration Example

#### Using Docker Infrastructure (Full)

```bash
# .env.prod
SPRING_PROFILES_ACTIVE=prod

# Docker infrastructure
DB_HOST=postgres
REDIS_HOST=redis
RABBITMQ_HOST=rabbitmq

# Strong passwords
DB_PASSWORD=<generated_secure_password>
REDIS_PASSWORD=<generated_secure_password>
RABBITMQ_PASSWORD=<generated_secure_password>
JWT_SECRET=<generated_32_char_secret>
```

#### Using External Services

```bash
# .env.prod
SPRING_PROFILES_ACTIVE=prod

# External PostgreSQL (e.g., RDS)
DB_HOST=your-db.region.rds.amazonaws.com
DB_PORT=5432
DB_NAME=actionow
DB_USER=actionow
DB_PASSWORD=your_db_password

# External Redis (e.g., ElastiCache)
REDIS_HOST=your-redis.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# External RabbitMQ (e.g., CloudAMQP)
RABBITMQ_HOST=your-rabbitmq.cloudamqp.com
RABBITMQ_PORT=5672
RABBITMQ_USER=your_user
RABBITMQ_PASSWORD=your_password
RABBITMQ_SSL_ENABLED=true
```

### Deployment Profiles

```bash
# Full deployment (Docker infrastructure + all apps)
docker compose -f docker-compose.prod.yml --profile full up -d --build

# Apps only (external infrastructure)
docker compose -f docker-compose.prod.yml --profile apps up -d --build

# Infrastructure only
docker compose -f docker-compose.prod.yml --profile infra up -d
```

### Memory Requirements

| Deployment Type | Minimum RAM | Recommended RAM |
|-----------------|-------------|-----------------|
| Development (partial) | 8 GB | 16 GB |
| Production (full) | 16 GB | 32 GB |

Per-service memory limits (configurable in .env.prod):

```bash
GATEWAY_MEMORY=1G
USER_MEMORY=1G
WORKSPACE_MEMORY=1G
WALLET_MEMORY=1G
PROJECT_MEMORY=1G
AI_MEMORY=2G        # Higher for AI processing
TASK_MEMORY=1G
COLLAB_MEMORY=1G
SYSTEM_MEMORY=1G
CANVAS_MEMORY=1G
AGENT_MEMORY=2G     # Higher for AI agent
```

### Security Checklist

- [ ] Change all default passwords
- [ ] Generate strong JWT_SECRET (min 32 characters)
- [ ] Use HTTPS in production (via reverse proxy)
- [ ] Enable RabbitMQ SSL for external connections
- [ ] Store credentials in secure secret manager
- [ ] Restrict port exposure (internal network only)
- [ ] Enable Nacos authentication in production

### Google Cloud / Vertex AI Setup

For Agent service with Google Cloud:

1. Create a service account in Google Cloud Console
2. Download the JSON key file
3. Place it in `docker/vertex_ai.json`
4. Configure environment variables:

```bash
GOOGLE_CLOUD_PROJECT=your-project-id
GOOGLE_CLOUD_LOCATION=us-central1
ADK_MODEL=gemini-2.0-flash
```

### Health Monitoring

All services expose health endpoints:

```bash
# Check individual service
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # User

# Check via deploy script
./deploy.sh status
```

### Updating Services

```bash
# Pull latest code
git pull origin main

# Rebuild and restart
./deploy.sh rebuild

# Or rebuild specific services
docker compose -f docker-compose.prod.yml --profile apps up -d --build gateway user
```

### Backup Recommendations

For production data:

1. **PostgreSQL**: Enable automated backups (RDS) or pg_dump
2. **Redis**: Enable persistence (AOF) or use managed service
3. **MinIO/S3**: Enable versioning and cross-region replication
4. **Volumes**: Regular volume backups for Docker deployments

---

## Development Notes

### Security Configuration

```bash
# Development defaults (NOT for production!)
JWT_SECRET=actionow-development-jwt-secret-key-change-in-production
POSTGRES_PASSWORD=actionow123
REDIS_PASSWORD=redis123
RABBITMQ_PASSWORD=actionow123
```

### Resource Configuration

Adjust JVM parameters based on server capacity:

```bash
# Development
JVM_XMS=256m
JVM_XMX=512m

# Production (8GB+ memory)
JVM_XMS=1g
JVM_XMX=2g
```

## Troubleshooting

### Services won't start

1. Check if infrastructure is healthy:
   ```bash
   ./actionow.sh health
   ```

2. Check logs:
   ```bash
   ./actionow.sh logs <service>
   ```

3. Ensure JARs are built:
   ```bash
   ./actionow.sh build
   ```

### Database connection issues

1. Wait for PostgreSQL to be ready:
   ```bash
   docker logs actionow-postgres
   ```

2. Check if schema exists:
   ```bash
   docker exec -it actionow-postgres psql -U actionow -d actionow -c '\dt'
   ```

### Nacos not ready

Nacos takes ~60 seconds to start. Wait and check:
```bash
curl http://localhost:8848/nacos/v1/console/health/readiness
```

### Port conflicts

Modify port mappings in `.env`:
```bash
GATEWAY_PORT=18080  # Change from 8080 to 18080
```

### Memory issues

Adjust Docker memory limit or reduce services:
```bash
# Only start core services for development
./actionow.sh up gateway user workspace
```

### Clean restart

```bash
./actionow.sh clean    # Removes all data!
./actionow.sh up
```

## Local Development

For local development, start only infrastructure:

```bash
# Start infrastructure
./actionow.sh infra

# Run services in IDE with 'dev' profile
```
