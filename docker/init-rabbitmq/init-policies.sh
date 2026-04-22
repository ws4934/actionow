#!/bin/sh
# 等待 RabbitMQ Management API 就绪，然后通过 API 创建 policy
# 所有参数均从环境变量读取

RABBITMQ_HOST="${RABBITMQ_HOST:-rabbitmq}"
RABBITMQ_USER="${RABBITMQ_DEFAULT_USER:-actionow}"
RABBITMQ_PASS="${RABBITMQ_DEFAULT_PASS:-actionow123}"
MANAGEMENT_PORT="${MANAGEMENT_PORT:-15672}"
API_URL="http://${RABBITMQ_HOST}:${MANAGEMENT_PORT}/api"

# 安装 curl
apk add --no-cache -q curl > /dev/null 2>&1

echo "[init-policies] Waiting for RabbitMQ Management API at ${API_URL}..."

for i in $(seq 1 30); do
    if curl -sf -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" "${API_URL}/overview" > /dev/null 2>&1; then
        echo "[init-policies] Management API is ready."
        break
    fi
    if [ "$i" = "30" ]; then
        echo "[init-policies] ERROR: Management API not ready after 30 attempts, skipping."
        exit 0
    fi
    sleep 2
done

# 创建 DLX policy
echo "[init-policies] Setting collab-dlx policy..."
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" \
    -X PUT \
    -H "Content-Type: application/json" \
    -d '{
        "pattern": "^(actionow\\.ws\\.notification|actionow\\.collab\\.|actionow\\.agent\\.).*",
        "apply-to": "queues",
        "definition": {
            "dead-letter-exchange": "actionow.dlx",
            "dead-letter-routing-key": "actionow.dlq"
        },
        "priority": 0
    }' \
    "${API_URL}/policies/%2F/collab-dlx")

if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "201" ]; then
    echo "[init-policies] Policy 'collab-dlx' created successfully (HTTP ${HTTP_CODE})."
else
    echo "[init-policies] WARNING: Policy creation returned HTTP ${HTTP_CODE}."
fi

echo "[init-policies] Done."
