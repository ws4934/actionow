package com.actionow.common.core.constant;

/**
 * 通用常量
 *
 * @author Actionow
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    // ==================== 状态常量 ====================
    /**
     * 启用状态
     */
    public static final int STATUS_ENABLED = 1;

    /**
     * 禁用状态
     */
    public static final int STATUS_DISABLED = 0;

    /**
     * 删除标识 - 已删除
     */
    public static final int DELETED = 1;

    /**
     * 删除标识 - 未删除
     */
    public static final int NOT_DELETED = 0;

    // ==================== 分页常量 ====================
    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 最大每页大小
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 请求头常量 ====================
    /**
     * Authorization 请求头
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Bearer Token 前缀
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 用户ID请求头
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 用户名请求头
     */
    public static final String HEADER_USERNAME = "X-Username";

    /**
     * 工作空间ID请求头
     */
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";

    /**
     * 租户Schema请求头
     */
    public static final String HEADER_TENANT_SCHEMA = "X-Tenant-Schema";

    /**
     * 请求ID请求头
     */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * 客户端IP请求头
     */
    public static final String HEADER_CLIENT_IP = "X-Forwarded-For";

    /**
     * 用户角色请求头
     */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * 用户邮箱请求头
     */
    public static final String HEADER_USER_EMAIL = "X-User-Email";

    /**
     * 网关时间戳请求头
     */
    public static final String HEADER_GATEWAY_TIME = "X-Gateway-Time";

    /**
     * 网关版本请求头
     */
    public static final String HEADER_GATEWAY_VERSION = "X-Gateway-Version";

    /**
     * 网关签名请求头（HMAC-SHA256）
     */
    public static final String HEADER_GATEWAY_SIGNATURE = "X-Gateway-Signature";

    /**
     * 内部服务认证令牌请求头（短时JWT）
     */
    public static final String HEADER_INTERNAL_AUTH_TOKEN = "X-Internal-Auth-Token";

    /**
     * 会话ID请求头
     */
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    // ==================== 时间常量（秒） ====================
    /**
     * 1分钟
     */
    public static final long MINUTE_SECONDS = 60L;

    /**
     * 1小时
     */
    public static final long HOUR_SECONDS = 3600L;

    /**
     * 1天
     */
    public static final long DAY_SECONDS = 86400L;

    /**
     * 1周
     */
    public static final long WEEK_SECONDS = 604800L;

    // ==================== 系统用户和系统工作空间 ====================
    /**
     * 系统用户ID
     */
    public static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * 系统用户名
     */
    public static final String SYSTEM_USERNAME = "system";

    /**
     * 系统工作空间ID
     */
    public static final String SYSTEM_WORKSPACE_ID = "00000000-0000-0000-0000-000000000001";

    /**
     * 系统租户Schema名称
     */
    public static final String SYSTEM_TENANT_SCHEMA = "tenant_system";

    /**
     * 系统级 scope 标识
     */
    public static final String SCOPE_SYSTEM = "SYSTEM";

    /**
     * 工作空间级 scope 标识
     */
    public static final String SCOPE_WORKSPACE = "WORKSPACE";

    /**
     * 剧本级 scope 标识
     */
    public static final String SCOPE_SCRIPT = "SCRIPT";
}
