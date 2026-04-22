#!/bin/bash
# =====================================================
# Actionow Init / Setup Wizard
# Interactive configuration for first-time deployment.
# Do NOT execute directly — sourced by deploy.sh.
# =====================================================

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

# Default passwords (generated fresh each run, used by init only)
DEFAULT_DB_PASSWORD=$(openssl rand -hex 12 2>/dev/null || echo "actionow_$(date +%s)")
DEFAULT_REDIS_PASSWORD=$(openssl rand -hex 8 2>/dev/null || echo "redis_$(date +%s)")
DEFAULT_RABBITMQ_PASSWORD=$(openssl rand -hex 8 2>/dev/null || echo "mq_$(date +%s)")
DEFAULT_MINIO_PASSWORD=$(openssl rand -hex 8 2>/dev/null || echo "minio_$(date +%s)")
DEFAULT_JWT_SECRET=$(openssl rand -hex 24 2>/dev/null || echo "jwt_secret_$(date +%s)_actionow")

# =====================================================
# Prompt Helpers
# =====================================================

prompt_yes_no() {
    local prompt="$1"
    local default="${2:-y}"
    local result

    if [ "$default" = "y" ]; then
        read -p "$prompt [Y/n]: " result
        result=${result:-y}
    else
        read -p "$prompt [y/N]: " result
        result=${result:-n}
    fi

    [[ "$result" =~ ^[Yy]$ ]]
}

prompt_value() {
    local prompt="$1"
    local default="$2"
    local is_secret="${3:-false}"
    local result

    if [ "$is_secret" = "true" ]; then
        read -sp "$prompt [$default]: " result
        echo >&2
    else
        read -p "$prompt [$default]: " result
    fi

    echo "${result:-$default}"
}

# =====================================================
# Configuration Sections
# =====================================================

configure_infrastructure() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Infrastructure Configuration${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    echo -e "${YELLOW}▸ PostgreSQL Configuration${NC}"
    if prompt_yes_no "  Use Docker for PostgreSQL?" "y"; then
        USE_DOCKER_PG="true"
        DB_HOST="postgres"
        DB_PORT="5432"
        DB_NAME=$(prompt_value "  PostgreSQL database" "actionow")
        DB_USER=$(prompt_value "  PostgreSQL username" "actionow")
        DB_PASSWORD=$(prompt_value "  PostgreSQL password" "$DEFAULT_DB_PASSWORD" true)
    else
        USE_DOCKER_PG="false"
        DB_HOST=$(prompt_value "  PostgreSQL host" "localhost")
        DB_PORT=$(prompt_value "  PostgreSQL port" "5432")
        DB_NAME=$(prompt_value "  PostgreSQL database" "actionow")
        DB_USER=$(prompt_value "  PostgreSQL username" "actionow")
        DB_PASSWORD=$(prompt_value "  PostgreSQL password" "" true)
    fi

    echo

    echo -e "${YELLOW}▸ Redis Configuration${NC}"
    if prompt_yes_no "  Use Docker for Redis?" "y"; then
        USE_DOCKER_REDIS="true"
        REDIS_HOST="redis"
        REDIS_PORT="6379"
        REDIS_PASSWORD=$(prompt_value "  Redis password" "$DEFAULT_REDIS_PASSWORD" true)
    else
        USE_DOCKER_REDIS="false"
        REDIS_HOST=$(prompt_value "  Redis host" "localhost")
        REDIS_PORT=$(prompt_value "  Redis port" "6379")
        REDIS_PASSWORD=$(prompt_value "  Redis password" "" true)
    fi

    echo

    echo -e "${YELLOW}▸ RabbitMQ Configuration${NC}"
    if prompt_yes_no "  Use Docker for RabbitMQ?" "y"; then
        USE_DOCKER_MQ="true"
        RABBITMQ_HOST="rabbitmq"
        RABBITMQ_PORT="5672"
        RABBITMQ_USER="actionow"
        RABBITMQ_PASSWORD=$(prompt_value "  RabbitMQ password" "$DEFAULT_RABBITMQ_PASSWORD" true)
    else
        USE_DOCKER_MQ="false"
        RABBITMQ_HOST=$(prompt_value "  RabbitMQ host" "localhost")
        RABBITMQ_PORT=$(prompt_value "  RabbitMQ port" "5672")
        RABBITMQ_USER=$(prompt_value "  RabbitMQ username" "guest")
        RABBITMQ_PASSWORD=$(prompt_value "  RabbitMQ password" "" true)
        RABBITMQ_SSL_ENABLED=$(prompt_yes_no "  Enable RabbitMQ SSL?" "n" && echo "true" || echo "false")
    fi

    echo

    echo -e "${YELLOW}▸ Object Storage Configuration${NC}"
    echo "  Available options: minio, s3, aliyun, r2, tos"
    OSS_TYPE=$(prompt_value "  Storage type" "minio")
    OSS_DOMAIN=$(prompt_value "  Public OSS domain (optional, applies to all providers)" "")

    case "$OSS_TYPE" in
        minio)
            if prompt_yes_no "  Use Docker for MinIO?" "y"; then
                USE_DOCKER_MINIO="true"
                MINIO_ENDPOINT="http://minio:9000"
                MINIO_ACCESS_KEY=$(prompt_value "  MinIO access key" "actionow")
                MINIO_SECRET_KEY=$(prompt_value "  MinIO secret key" "$DEFAULT_MINIO_PASSWORD" true)
            else
                USE_DOCKER_MINIO="false"
                MINIO_ENDPOINT=$(prompt_value "  MinIO endpoint" "http://localhost:9000")
                MINIO_ACCESS_KEY=$(prompt_value "  MinIO access key" "")
                MINIO_SECRET_KEY=$(prompt_value "  MinIO secret key" "" true)
            fi
            MINIO_BUCKET_NAME=$(prompt_value "  MinIO bucket name" "actionow-assets")
            ;;
        s3)
            USE_DOCKER_MINIO="false"
            S3_ACCESS_KEY_ID=$(prompt_value "  AWS Access Key ID" "")
            S3_SECRET_ACCESS_KEY=$(prompt_value "  AWS Secret Access Key" "" true)
            S3_REGION=$(prompt_value "  AWS Region" "us-east-1")
            S3_BUCKET=$(prompt_value "  S3 Bucket name" "")
            S3_CLOUDFRONT_DOMAIN=$(prompt_value "  CloudFront domain (optional)" "")
            if [ -n "$S3_CLOUDFRONT_DOMAIN" ]; then
                S3_CLOUDFRONT_KEY_PAIR_ID=$(prompt_value "  CloudFront Key Pair ID (optional, for signed URLs)" "")
                S3_CLOUDFRONT_PRIVATE_KEY_PATH=$(prompt_value "  CloudFront private key path (optional)" "")
            fi
            ;;
        aliyun)
            USE_DOCKER_MINIO="false"
            ALIYUN_ENDPOINT=$(prompt_value "  Aliyun OSS endpoint" "")
            ALIYUN_INTERNAL_ENDPOINT=$(prompt_value "  Aliyun internal endpoint (VPC, optional)" "")
            ALIYUN_ACCESS_KEY_ID=$(prompt_value "  Aliyun Access Key ID" "")
            ALIYUN_ACCESS_KEY_SECRET=$(prompt_value "  Aliyun Access Key Secret" "" true)
            ALIYUN_BUCKET=$(prompt_value "  Aliyun bucket name" "")
            ;;
        r2)
            USE_DOCKER_MINIO="false"
            R2_ACCOUNT_ID=$(prompt_value "  Cloudflare Account ID" "")
            R2_ACCESS_KEY_ID=$(prompt_value "  R2 Access Key ID" "")
            R2_SECRET_ACCESS_KEY=$(prompt_value "  R2 Secret Access Key" "" true)
            R2_BUCKET=$(prompt_value "  R2 bucket name" "")
            R2_PUBLIC_DOMAIN=$(prompt_value "  R2 public domain (optional)" "")
            ;;
        tos)
            USE_DOCKER_MINIO="false"
            TOS_ENDPOINT=$(prompt_value "  TOS endpoint" "tos-cn-guangzhou.volces.com")
            TOS_ACCESS_KEY_ID=$(prompt_value "  TOS Access Key ID" "")
            TOS_SECRET_ACCESS_KEY=$(prompt_value "  TOS Secret Access Key" "" true)
            TOS_BUCKET=$(prompt_value "  TOS bucket name" "")
            TOS_REGION=$(prompt_value "  TOS region" "cn-guangzhou")
            TOS_PUBLIC_DOMAIN=$(prompt_value "  TOS public domain (optional)" "")
            ;;
    esac
}

configure_security() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Security Configuration${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    JWT_SECRET=$(prompt_value "JWT Secret (min 32 chars)" "$DEFAULT_JWT_SECRET" true)

    if [ ${#JWT_SECRET} -lt 32 ]; then
        log_warn "JWT secret too short. Generating secure secret..."
        JWT_SECRET="$DEFAULT_JWT_SECRET"
    fi

    INTERNAL_AUTH_SECRET=$(openssl rand -hex 32 2>/dev/null || echo "CHANGE_ME_TO_RANDOM_HEX_64")
}

configure_ai_providers() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  AI Provider Configuration (Optional)${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo
    echo -e "${YELLOW}  Tip: New models can be added later without a release${NC}"
    echo -e "${YELLOW}        via the Groovy-sandbox model gateway in actionow-ai.${NC}"
    echo

    if prompt_yes_no "Configure AI providers now?" "n"; then
        echo -e "${YELLOW}▸ OpenAI / Compatible (Azure, self-hosted)${NC}"
        OPENAI_API_KEY=$(prompt_value "  OpenAI API Key" "" true)
        OPENAI_BASE_URL=$(prompt_value "  OpenAI Base URL" "https://api.openai.com")

        echo -e "${YELLOW}▸ Anthropic${NC}"
        ANTHROPIC_API_KEY=$(prompt_value "  Anthropic API Key" "" true)

        echo -e "${YELLOW}▸ Stability AI${NC}"
        STABILITY_API_KEY=$(prompt_value "  Stability API Key" "" true)

        echo -e "${YELLOW}▸ Google Cloud / Vertex AI${NC}"
        GOOGLE_API_KEY=$(prompt_value "  Google API Key" "" true)
        GOOGLE_CLOUD_PROJECT=$(prompt_value "  Google Cloud Project ID" "")
        GOOGLE_CLOUD_LOCATION=$(prompt_value "  Google Cloud Location" "us-central1")
    fi
}

configure_oauth() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  OAuth Configuration${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo
    echo -e "${YELLOW}  OAuth 配置已迁移至 t_system_config${NC}"
    echo -e "${YELLOW}  请通过 System 管理面板配置 OAuth 提供商${NC}"
    echo
}

configure_mail() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Mail Configuration (Optional)${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    if prompt_yes_no "Configure mail service now?" "n"; then
        echo "  Available providers: resend, smtp, cloudflare"
        MAIL_PROVIDER=$(prompt_value "  Mail provider" "resend")
        MAIL_FROM=$(prompt_value "  Sender email address" "support@actionow.ai")
        MAIL_FROM_NAME=$(prompt_value "  Sender display name" "Actionow")

        case "$MAIL_PROVIDER" in
            resend)
                RESEND_API_KEY=$(prompt_value "  Resend API Key" "" true)
                ;;
            smtp)
                MAIL_HOST=$(prompt_value "  SMTP host" "email-smtp.ap-northeast-1.amazonaws.com")
                MAIL_PORT=$(prompt_value "  SMTP port" "587")
                MAIL_USERNAME=$(prompt_value "  SMTP username" "")
                MAIL_PASSWORD=$(prompt_value "  SMTP password" "" true)
                ;;
            cloudflare)
                CLOUDFLARE_MAIL_ACCOUNT_ID=$(prompt_value "  Cloudflare Account ID" "")
                CLOUDFLARE_MAIL_API_TOKEN=$(prompt_value "  Cloudflare API Token" "" true)
                CLOUDFLARE_MAIL_API_BASE=$(prompt_value "  Cloudflare API base" "https://api.cloudflare.com/client/v4")
                CLOUDFLARE_MAIL_TIMEOUT_MS=$(prompt_value "  Request timeout (ms)" "10000")
                ;;
            *)
                log_warn "Unknown provider '$MAIL_PROVIDER', falling back to resend defaults."
                MAIL_PROVIDER=resend
                ;;
        esac
    fi
}

configure_billing() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Billing / Payments (Optional)${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    SITE_URL=$(prompt_value "Public site URL (used for Stripe redirects)" "http://localhost:3000")
    BILLING_SUBSCRIPTION_CURRENCY=$(prompt_value "Subscription currency" "USD")

    if prompt_yes_no "Configure Stripe?" "n"; then
        STRIPE_API_KEY=$(prompt_value "  Stripe API Key" "" true)
        STRIPE_WEBHOOK_SECRET=$(prompt_value "  Stripe Webhook Secret" "" true)
    fi

    if prompt_yes_no "Configure WeChat Pay?" "n"; then
        WECHAT_APP_ID=$(prompt_value "  WeChat AppId" "")
        WECHAT_MCH_ID=$(prompt_value "  WeChat Merchant ID" "")
        WECHAT_API_V3_KEY=$(prompt_value "  WeChat API v3 Key" "" true)
        WECHAT_MCH_SERIAL_NUMBER=$(prompt_value "  WeChat merchant cert serial number" "")
        WECHAT_PRIVATE_KEY_PATH=$(prompt_value "  WeChat private key path" "classpath:cert/apiclient_key.pem")
        WECHAT_NOTIFY_URL=$(prompt_value "  WeChat notify URL (must be public HTTPS)" "https://api.your-domain.com/billing/callback/WECHATPAY")
    fi
}

configure_web() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Web Frontend (Next.js)${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    WEB_PORT=$(prompt_value "Web container host port" "3000")
    WEB_NEXT_PUBLIC_SITE_URL=$(prompt_value "Public site URL (browser-facing)" "${SITE_URL:-http://localhost:$WEB_PORT}")
    WEB_API_BASE_URL=$(prompt_value "API base URL (in-network)" "http://actionow-gateway:8080")
    WEB_NEXT_PUBLIC_WS_URL=$(prompt_value "WebSocket URL (browser-facing)" "ws://localhost:8080/ws")
    WEB_MEMORY=$(prompt_value "Web container memory limit" "1G")
    WEB_IMAGE_TAG=$(prompt_value "Web image tag" "latest")
}

configure_advanced() {
    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Advanced Tuning (Optional)${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo

    if ! prompt_yes_no "Customize advanced settings (ports, memory, Agent/SAA, Task, Dify, sessions, missions)?" "n"; then
        return
    fi

    echo -e "${YELLOW}▸ AI Extensions${NC}"
    DIFY_BASE_URL=$(prompt_value "  Dify base URL" "http://localhost:5001")
    GROOVY_MAX_EXECUTION_TIME_MS=$(prompt_value "  Groovy sandbox timeout (ms)" "120000")
    ADK_MODEL=$(prompt_value "  ADK model" "gemini-2.0-flash")
    ADK_MAX_ITERATIONS=$(prompt_value "  ADK max iterations" "15")
    EMBEDDING_MODEL=$(prompt_value "  Embedding model" "text-embedding-004")

    echo -e "${YELLOW}▸ Agent (SAA)${NC}"
    SAA_DEFAULT_MODEL=$(prompt_value "  SAA default model ID" "gemini-3-flash-preview")
    SAA_MAX_ITERATIONS=$(prompt_value "  SAA max ReAct iterations" "20")
    RAG_ENABLED=$(prompt_yes_no "  Enable RAG (requires pgvector)?" "n" && echo "true" || echo "false")
    SKILL_PACKAGE_URL=$(prompt_value "  Skill package URL (optional)" "")

    echo -e "${YELLOW}▸ Agent Billing${NC}"
    BILLING_IDLE_TIMEOUT=$(prompt_value "  Billing idle timeout (s)" "30")
    BILLING_BATCH_SIZE=$(prompt_value "  Billing batch size" "50")
    BILLING_MAX_RETRY=$(prompt_value "  Billing max retry" "3")
    BILLING_SETTLE_INTERVAL=$(prompt_value "  Billing settle interval (ms)" "300000")
    BILLING_RETRY_INTERVAL=$(prompt_value "  Billing retry interval (ms)" "600000")

    echo -e "${YELLOW}▸ Agent Session${NC}"
    SESSION_MAX_ACTIVE_PER_SCOPE=$(prompt_value "  Max active sessions per scope" "5")
    SESSION_MAX_ACTIVE_GLOBAL=$(prompt_value "  Max active sessions global" "3")
    SESSION_MAX_TOTAL_PER_USER=$(prompt_value "  Max total sessions per user" "200")
    SESSION_IDLE_ARCHIVE_DAYS=$(prompt_value "  Idle archive days" "30")
    SESSION_SOFT_DELETE_RETENTION=$(prompt_value "  Soft delete retention days" "90")
    SESSION_BATCH_SIZE=$(prompt_value "  Session batch size" "100")

    echo -e "${YELLOW}▸ Agent Mission${NC}"
    MISSION_MAX_STEPS=$(prompt_value "  Mission max steps" "50")
    MISSION_MAX_RETRIES=$(prompt_value "  Mission max retries" "3")
    MISSION_MAX_CONTEXT_STEPS=$(prompt_value "  Mission max context steps" "10")
    MISSION_MAX_STEP_SUMMARY_CHARS=$(prompt_value "  Mission step summary chars" "500")
    MISSION_LOOP_WARN_THRESHOLD=$(prompt_value "  Loop warn threshold" "5")
    MISSION_LOOP_FAIL_THRESHOLD=$(prompt_value "  Loop fail threshold" "10")

    echo -e "${YELLOW}▸ Task Service${NC}"
    TASK_CALLBACK_BASE_URL=$(prompt_value "  Task callback base URL" "http://actionow-task:8087")

    echo -e "${YELLOW}▸ Service Ports${NC}"
    GATEWAY_PORT=$(prompt_value "  Gateway port" "8080")
    USER_PORT=$(prompt_value "  User service port" "8081")
    WORKSPACE_PORT=$(prompt_value "  Workspace port" "8082")
    WALLET_PORT=$(prompt_value "  Wallet port" "8083")
    PROJECT_PORT=$(prompt_value "  Project port" "8084")
    AI_PORT=$(prompt_value "  AI port" "8086")
    TASK_PORT=$(prompt_value "  Task port" "8087")
    COLLAB_PORT=$(prompt_value "  Collab port" "8088")
    SYSTEM_PORT=$(prompt_value "  System port" "8089")
    CANVAS_PORT=$(prompt_value "  Canvas port" "8090")
    AGENT_PORT=$(prompt_value "  Agent port" "8091")
    BILLING_PORT=$(prompt_value "  Billing port" "8092")

    echo -e "${YELLOW}▸ Infrastructure Expose Ports${NC}"
    POSTGRES_EXPOSE_PORT=$(prompt_value "  PostgreSQL expose port" "5432")
    REDIS_EXPOSE_PORT=$(prompt_value "  Redis expose port" "6379")
    RABBITMQ_EXPOSE_PORT=$(prompt_value "  RabbitMQ expose port" "5672")
    RABBITMQ_MANAGEMENT_PORT=$(prompt_value "  RabbitMQ management port" "15672")
    MINIO_PORT=$(prompt_value "  MinIO API port" "9000")
    MINIO_CONSOLE_PORT=$(prompt_value "  MinIO console port" "9001")

    echo -e "${YELLOW}▸ JVM / Memory${NC}"
    JVM_OPTS=$(prompt_value "  JVM opts" "-XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication")
    GATEWAY_MEMORY=$(prompt_value "  Gateway memory" "1G")
    USER_MEMORY=$(prompt_value "  User memory" "1G")
    WORKSPACE_MEMORY=$(prompt_value "  Workspace memory" "1G")
    WALLET_MEMORY=$(prompt_value "  Wallet memory" "1G")
    PROJECT_MEMORY=$(prompt_value "  Project memory" "1G")
    AI_MEMORY=$(prompt_value "  AI memory" "2G")
    TASK_MEMORY=$(prompt_value "  Task memory" "1G")
    COLLAB_MEMORY=$(prompt_value "  Collab memory" "1G")
    SYSTEM_MEMORY=$(prompt_value "  System memory" "1G")
    CANVAS_MEMORY=$(prompt_value "  Canvas memory" "1G")
    AGENT_MEMORY=$(prompt_value "  Agent memory" "2G")
    BILLING_MEMORY=$(prompt_value "  Billing memory" "1G")
}

# =====================================================
# Environment File Generation
# =====================================================

generate_env_file() {
    log_info "Generating environment configuration..."

    cat > "$ENV_FILE" << EOF
# =====================================================
# Actionow Production Environment Configuration
# Generated on: $(date)
# =====================================================

# Common
TZ=Asia/Shanghai
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
LOG_LEVEL=${LOG_LEVEL:-INFO}
COMPOSE_PROJECT_NAME=actionow

# =====================================================
# Infrastructure
# =====================================================

# PostgreSQL
DB_HOST=${DB_HOST:-postgres}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-actionow}
DB_USER=${DB_USER:-actionow}
DB_PASSWORD="${DB_PASSWORD}"

# Redis
REDIS_HOST=${REDIS_HOST:-redis}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD="${REDIS_PASSWORD}"

# RabbitMQ
RABBITMQ_HOST=${RABBITMQ_HOST:-rabbitmq}
RABBITMQ_PORT=${RABBITMQ_PORT:-5672}
RABBITMQ_USER=${RABBITMQ_USER:-actionow}
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD}"
RABBITMQ_VHOST=/
RABBITMQ_SSL_ENABLED=${RABBITMQ_SSL_ENABLED:-false}

# =====================================================
# Object Storage
# =====================================================

OSS_TYPE=${OSS_TYPE:-minio}
OSS_DOMAIN=${OSS_DOMAIN:-}

# MinIO
MINIO_ENDPOINT=${MINIO_ENDPOINT:-http://minio:9000}
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY:-actionow}
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET_NAME=${MINIO_BUCKET_NAME:-actionow-assets}
MINIO_ROOT_USER=${MINIO_ACCESS_KEY:-actionow}
MINIO_ROOT_PASSWORD="${MINIO_SECRET_KEY:-minioadmin}"

# AWS S3
S3_ACCESS_KEY_ID="${S3_ACCESS_KEY_ID:-}"
S3_SECRET_ACCESS_KEY="${S3_SECRET_ACCESS_KEY:-}"
S3_REGION=${S3_REGION:-us-east-1}
S3_BUCKET=${S3_BUCKET:-}
S3_TRANSFER_ACCELERATION=false
S3_CLOUDFRONT_DOMAIN=${S3_CLOUDFRONT_DOMAIN:-}
S3_CLOUDFRONT_KEY_PAIR_ID=${S3_CLOUDFRONT_KEY_PAIR_ID:-}
S3_CLOUDFRONT_PRIVATE_KEY_PATH=${S3_CLOUDFRONT_PRIVATE_KEY_PATH:-}
S3_PRESIGNED_EXPIRE=3600

# Cloudflare R2
R2_ACCOUNT_ID=${R2_ACCOUNT_ID:-}
R2_ACCESS_KEY_ID="${R2_ACCESS_KEY_ID:-}"
R2_SECRET_ACCESS_KEY="${R2_SECRET_ACCESS_KEY:-}"
R2_BUCKET=${R2_BUCKET:-}
R2_PUBLIC_DOMAIN=${R2_PUBLIC_DOMAIN:-}
R2_PRESIGNED_EXPIRE=3600

# Aliyun OSS
ALIYUN_ENDPOINT=${ALIYUN_ENDPOINT:-}
ALIYUN_INTERNAL_ENDPOINT=${ALIYUN_INTERNAL_ENDPOINT:-}
ALIYUN_ACCESS_KEY_ID="${ALIYUN_ACCESS_KEY_ID:-}"
ALIYUN_ACCESS_KEY_SECRET="${ALIYUN_ACCESS_KEY_SECRET:-}"
ALIYUN_BUCKET=${ALIYUN_BUCKET:-}
ALIYUN_PRESIGNED_EXPIRE=3600

# Volcengine TOS
TOS_ENDPOINT=${TOS_ENDPOINT:-tos-cn-guangzhou.volces.com}
TOS_ACCESS_KEY_ID="${TOS_ACCESS_KEY_ID:-}"
TOS_SECRET_ACCESS_KEY="${TOS_SECRET_ACCESS_KEY:-}"
TOS_BUCKET=${TOS_BUCKET:-}
TOS_REGION=${TOS_REGION:-cn-guangzhou}
TOS_PUBLIC_DOMAIN=${TOS_PUBLIC_DOMAIN:-}
TOS_PRESIGNED_EXPIRE=3600

# =====================================================
# Security
# =====================================================

JWT_SECRET="${JWT_SECRET}"
INTERNAL_AUTH_SECRET="${INTERNAL_AUTH_SECRET}"

# =====================================================
# Billing / Stripe
# =====================================================

SITE_URL=${SITE_URL:-http://localhost:3000}
STRIPE_API_KEY="${STRIPE_API_KEY:-}"
STRIPE_WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-}"
STRIPE_SUCCESS_URL=${STRIPE_SUCCESS_URL:-${SITE_URL:-http://localhost:3000}/billing/success?session_id={CHECKOUT_SESSION_ID}}
STRIPE_CANCEL_URL=${STRIPE_CANCEL_URL:-${SITE_URL:-http://localhost:3000}/billing/cancel}
BILLING_SUBSCRIPTION_CURRENCY=${BILLING_SUBSCRIPTION_CURRENCY:-USD}
BILLING_POINTS_PER_MAJOR_UNIT=10
BILLING_MINOR_PER_MAJOR_UNIT=100

# WeChat Pay (Native scan-to-pay for topup)
WECHAT_APP_ID=${WECHAT_APP_ID:-}
WECHAT_MCH_ID=${WECHAT_MCH_ID:-}
WECHAT_API_V3_KEY="${WECHAT_API_V3_KEY:-}"
WECHAT_PRIVATE_KEY_PATH=${WECHAT_PRIVATE_KEY_PATH:-classpath:cert/apiclient_key.pem}
WECHAT_MCH_SERIAL_NUMBER=${WECHAT_MCH_SERIAL_NUMBER:-}
WECHAT_NOTIFY_URL=${WECHAT_NOTIFY_URL:-http://localhost:8092/billing/callback/WECHATPAY}

# =====================================================
# AI Providers
# =====================================================

OPENAI_API_KEY="${OPENAI_API_KEY:-}"
OPENAI_BASE_URL=${OPENAI_BASE_URL:-https://api.openai.com}
ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}"
STABILITY_API_KEY="${STABILITY_API_KEY:-}"

# Dify Integration
DIFY_BASE_URL=${DIFY_BASE_URL:-http://localhost:5001}

# Groovy Sandbox (AI service script execution timeout)
GROOVY_MAX_EXECUTION_TIME_MS=${GROOVY_MAX_EXECUTION_TIME_MS:-120000}

# Google Cloud / Vertex AI
GOOGLE_API_KEY="${GOOGLE_API_KEY:-}"
GOOGLE_CLOUD_PROJECT=${GOOGLE_CLOUD_PROJECT:-}
GOOGLE_CLOUD_LOCATION=${GOOGLE_CLOUD_LOCATION:-us-central1}
ADK_MODEL=${ADK_MODEL:-gemini-2.0-flash}
ADK_MAX_ITERATIONS=${ADK_MAX_ITERATIONS:-15}
EMBEDDING_MODEL=${EMBEDDING_MODEL:-text-embedding-004}

# =====================================================
# Agent (SAA - Spring AI Alibaba)
# =====================================================

# Default LLM model ID (from t_llm_provider table)
SAA_DEFAULT_MODEL=${SAA_DEFAULT_MODEL:-gemini-3-flash-preview}
SAA_MAX_ITERATIONS=${SAA_MAX_ITERATIONS:-20}

# Enable RAG (vector memory) — requires pgvector
RAG_ENABLED=${RAG_ENABLED:-false}

# Agent Billing
BILLING_IDLE_TIMEOUT=${BILLING_IDLE_TIMEOUT:-30}
BILLING_BATCH_SIZE=${BILLING_BATCH_SIZE:-50}
BILLING_MAX_RETRY=${BILLING_MAX_RETRY:-3}
BILLING_SETTLE_INTERVAL=${BILLING_SETTLE_INTERVAL:-300000}
BILLING_RETRY_INTERVAL=${BILLING_RETRY_INTERVAL:-600000}

# Agent Session
SESSION_MAX_ACTIVE_PER_SCOPE=${SESSION_MAX_ACTIVE_PER_SCOPE:-5}
SESSION_MAX_ACTIVE_GLOBAL=${SESSION_MAX_ACTIVE_GLOBAL:-3}
SESSION_MAX_TOTAL_PER_USER=${SESSION_MAX_TOTAL_PER_USER:-200}
SESSION_IDLE_ARCHIVE_DAYS=${SESSION_IDLE_ARCHIVE_DAYS:-30}
SESSION_SOFT_DELETE_RETENTION=${SESSION_SOFT_DELETE_RETENTION:-90}
SESSION_BATCH_SIZE=${SESSION_BATCH_SIZE:-100}

# Agent Mission (Autonomous Execution)
MISSION_MAX_STEPS=${MISSION_MAX_STEPS:-50}
MISSION_MAX_RETRIES=${MISSION_MAX_RETRIES:-3}
MISSION_MAX_CONTEXT_STEPS=${MISSION_MAX_CONTEXT_STEPS:-10}
MISSION_MAX_STEP_SUMMARY_CHARS=${MISSION_MAX_STEP_SUMMARY_CHARS:-500}
MISSION_LOOP_WARN_THRESHOLD=${MISSION_LOOP_WARN_THRESHOLD:-5}
MISSION_LOOP_FAIL_THRESHOLD=${MISSION_LOOP_FAIL_THRESHOLD:-10}

# Skill Package (OSS ZIP) — leave empty to manage via Admin API only
SKILL_PACKAGE_URL=${SKILL_PACKAGE_URL:-}

# =====================================================
# OAuth - 已迁移至 t_system_config，通过 System 管理面板配置
# =====================================================

# =====================================================
# Mail
# =====================================================

MAIL_PROVIDER=${MAIL_PROVIDER:-resend}
MAIL_FROM=${MAIL_FROM:-support@actionow.ai}
MAIL_FROM_NAME=${MAIL_FROM_NAME:-Actionow}

# Resend
RESEND_API_KEY="${RESEND_API_KEY:-}"

# SMTP (e.g., AWS SES)
MAIL_HOST=${MAIL_HOST:-}
MAIL_PORT=${MAIL_PORT:-587}
MAIL_USERNAME=${MAIL_USERNAME:-}
MAIL_PASSWORD="${MAIL_PASSWORD:-}"

# Cloudflare Email Sending API
CLOUDFLARE_MAIL_ACCOUNT_ID=${CLOUDFLARE_MAIL_ACCOUNT_ID:-}
CLOUDFLARE_MAIL_API_TOKEN="${CLOUDFLARE_MAIL_API_TOKEN:-}"
CLOUDFLARE_MAIL_API_BASE=${CLOUDFLARE_MAIL_API_BASE:-https://api.cloudflare.com/client/v4}
CLOUDFLARE_MAIL_TIMEOUT_MS=${CLOUDFLARE_MAIL_TIMEOUT_MS:-10000}

# =====================================================
# Task Service
# =====================================================

TASK_CALLBACK_BASE_URL=${TASK_CALLBACK_BASE_URL:-http://actionow-task:8087}

# =====================================================
# Web Frontend (Next.js)
# =====================================================

WEB_PORT=${WEB_PORT:-3000}
WEB_MEMORY=${WEB_MEMORY:-1G}
WEB_IMAGE_TAG=${WEB_IMAGE_TAG:-latest}
WEB_API_BASE_URL=${WEB_API_BASE_URL:-http://actionow-gateway:8080}
WEB_NEXT_PUBLIC_WS_URL=${WEB_NEXT_PUBLIC_WS_URL:-ws://localhost:8080/ws}
WEB_NEXT_PUBLIC_SITE_URL=${WEB_NEXT_PUBLIC_SITE_URL:-http://localhost:3000}

# =====================================================
# Service Ports
# =====================================================

GATEWAY_PORT=${GATEWAY_PORT:-8080}
USER_PORT=${USER_PORT:-8081}
WORKSPACE_PORT=${WORKSPACE_PORT:-8082}
WALLET_PORT=${WALLET_PORT:-8083}
PROJECT_PORT=${PROJECT_PORT:-8084}
AI_PORT=${AI_PORT:-8086}
TASK_PORT=${TASK_PORT:-8087}
COLLAB_PORT=${COLLAB_PORT:-8088}
SYSTEM_PORT=${SYSTEM_PORT:-8089}
CANVAS_PORT=${CANVAS_PORT:-8090}
AGENT_PORT=${AGENT_PORT:-8091}
BILLING_PORT=${BILLING_PORT:-8092}

# Infrastructure expose ports (change if conflicts)
POSTGRES_EXPOSE_PORT=${POSTGRES_EXPOSE_PORT:-5432}
REDIS_EXPOSE_PORT=${REDIS_EXPOSE_PORT:-6379}
RABBITMQ_EXPOSE_PORT=${RABBITMQ_EXPOSE_PORT:-5672}
RABBITMQ_MANAGEMENT_PORT=${RABBITMQ_MANAGEMENT_PORT:-15672}
MINIO_PORT=${MINIO_PORT:-9000}
MINIO_CONSOLE_PORT=${MINIO_CONSOLE_PORT:-9001}

# =====================================================
# Service URL Overrides (apps mode only)
# When using COMPOSE_MODE=apps with host network, uncomment and set these
# to 127.0.0.1 addresses. In Docker bridge mode they resolve automatically.
# =====================================================
#USER_SERVICE_URL=http://127.0.0.1:8081
#WORKSPACE_SERVICE_URL=http://127.0.0.1:8082
#WALLET_SERVICE_URL=http://127.0.0.1:8083
#PROJECT_SERVICE_URL=http://127.0.0.1:8084
#AI_SERVICE_URL=http://127.0.0.1:8086
#TASK_SERVICE_URL=http://127.0.0.1:8087
#COLLAB_SERVICE_URL=http://127.0.0.1:8088
#SYSTEM_SERVICE_URL=http://127.0.0.1:8089
#CANVAS_SERVICE_URL=http://127.0.0.1:8090
#AGENT_SERVICE_URL=http://127.0.0.1:8091
#BILLING_SERVICE_URL=http://127.0.0.1:8092
#COLLAB_WS_URL=ws://127.0.0.1:8088
#CANVAS_WS_URL=ws://127.0.0.1:8090

# =====================================================
# JVM Configuration
# Heap is auto-sized by MaxRAMPercentage=75% of container memory limit.
# =====================================================

JVM_OPTS=${JVM_OPTS:--XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication}

# Per-service memory limits
GATEWAY_MEMORY=${GATEWAY_MEMORY:-1G}
USER_MEMORY=${USER_MEMORY:-1G}
WORKSPACE_MEMORY=${WORKSPACE_MEMORY:-1G}
WALLET_MEMORY=${WALLET_MEMORY:-1G}
PROJECT_MEMORY=${PROJECT_MEMORY:-1G}
AI_MEMORY=${AI_MEMORY:-2G}
TASK_MEMORY=${TASK_MEMORY:-1G}
COLLAB_MEMORY=${COLLAB_MEMORY:-1G}
SYSTEM_MEMORY=${SYSTEM_MEMORY:-1G}
CANVAS_MEMORY=${CANVAS_MEMORY:-1G}
AGENT_MEMORY=${AGENT_MEMORY:-2G}
BILLING_MEMORY=${BILLING_MEMORY:-1G}
EOF

    log_success "Environment file created: $ENV_FILE"
}

# =====================================================
# Init Command
# =====================================================

cmd_init() {
    print_banner
    check_docker

    if [ -f "$ENV_FILE" ]; then
        if ! prompt_yes_no "Configuration exists at $ENV_FILE. Overwrite?" "n"; then
            log_info "Using existing configuration."
            return
        fi
        local backup="${ENV_FILE}.bak.$(date +%Y%m%d-%H%M%S)"
        cp "$ENV_FILE" "$backup"
        log_info "Existing config backed up to $backup"
    fi

    echo
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    echo -e "${BLUE}  General${NC}"
    echo -e "${BLUE}════════════════════════════════════════${NC}"
    SPRING_PROFILES_ACTIVE=$(prompt_value "Spring profile (dev/docker/prod)" "docker")
    LOG_LEVEL=$(prompt_value "Log level (DEBUG/INFO/WARN/ERROR)" "INFO")

    configure_infrastructure
    configure_security
    configure_ai_providers
    configure_oauth
    configure_mail
    configure_billing
    configure_web
    configure_advanced

    generate_env_file
    regenerate_rabbitmq_definitions

    echo
    if prompt_yes_no "Start deployment now?" "y"; then
        cmd_rebuild
    else
        log_info "Configuration saved. Run '$0 up' to start services."
    fi
}

# =====================================================
# RabbitMQ definitions.json — password hash must match RABBITMQ_PASSWORD
# because load_definitions disables RABBITMQ_DEFAULT_USER/PASS seeding.
# =====================================================
regenerate_rabbitmq_definitions() {
    local defs_file
    defs_file="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/init-rabbitmq/definitions.json"

    if [ ! -d "$(dirname "$defs_file")" ]; then
        log_warn "init-rabbitmq dir not found; skipping RabbitMQ definitions regen."
        return
    fi

    if ! command -v python3 >/dev/null 2>&1; then
        log_warn "python3 not available; cannot regenerate RabbitMQ password hash."
        log_warn "You must manually update $defs_file with the new password hash,"
        log_warn "or delete the 'actionow' user block and let RabbitMQ seed from env."
        return
    fi

    local user="${RABBITMQ_USER:-actionow}"
    local hash
    hash=$(python3 -c "
import os, hashlib, base64, sys
pwd = sys.argv[1].encode('utf-8')
salt = os.urandom(4)
h = hashlib.sha256(salt + pwd).digest()
print(base64.b64encode(salt + h).decode())
" "$RABBITMQ_PASSWORD")

    cat > "$defs_file" << EOF
{
  "vhosts": [
    { "name": "/" }
  ],
  "users": [
    {
      "name": "${user}",
      "password_hash": "${hash}",
      "hashing_algorithm": "rabbit_password_hashing_sha256",
      "tags": ["administrator"]
    }
  ],
  "permissions": [
    {
      "user": "${user}",
      "vhost": "/",
      "configure": ".*",
      "write": ".*",
      "read": ".*"
    }
  ],
  "policies": [
    {
      "vhost": "/",
      "name": "collab-dlx",
      "pattern": "^(actionow\\\\.ws\\\\.notification|actionow\\\\.collab\\\\.|actionow\\\\.agent\\\\.).*",
      "apply-to": "queues",
      "definition": {
        "dead-letter-exchange": "actionow.dlx",
        "dead-letter-routing-key": "actionow.dlq"
      },
      "priority": 0
    }
  ]
}
EOF
    log_success "RabbitMQ definitions regenerated with current password: $defs_file"
}
