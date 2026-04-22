package com.actionow.common.core.constant;

/**
 * Redis 缓存 Key 常量
 *
 * @author Actionow
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /**
     * Key 前缀
     */
    public static final String PREFIX = "actionow:";

    // ==================== 用户服务 ====================
    /**
     * 用户信息缓存 actionow:user:info:{userId}
     */
    public static final String USER_INFO = PREFIX + "user:info:";

    /**
     * 用户Token缓存 actionow:user:token:{userId}
     */
    public static final String USER_TOKEN = PREFIX + "user:token:";

    /**
     * 用户RefreshToken actionow:user:refresh:{refreshToken}
     */
    public static final String USER_REFRESH_TOKEN = PREFIX + "user:refresh:";

    /**
     * 验证码缓存 actionow:user:captcha:{key}
     */
    public static final String CAPTCHA = PREFIX + "user:captcha:";

    /**
     * 登录失败次数 actionow:user:login:fail:{username}
     */
    public static final String LOGIN_FAIL_COUNT = PREFIX + "user:login:fail:";

    /**
     * OAuth State actionow:user:oauth:state:{state}
     */
    public static final String OAUTH_STATE = PREFIX + "user:oauth:state:";

    // ==================== 系统配置 ====================
    /**
     * 系统配置缓存 actionow:system:config:{key}
     */
    public static final String SYSTEM_CONFIG = PREFIX + "system:config:";

    // ==================== 工作空间服务 ====================
    /**
     * 工作空间信息 actionow:workspace:info:{workspaceId}
     */
    public static final String WORKSPACE_INFO = PREFIX + "workspace:info:";

    /**
     * 邀请码 actionow:workspace:invite:{code}
     */
    public static final String WORKSPACE_INVITE = PREFIX + "workspace:invite:";

    /**
     * 成员列表 actionow:workspace:members:{workspaceId}
     */
    public static final String WORKSPACE_MEMBERS = PREFIX + "workspace:members:";

    // ==================== 钱包服务 ====================
    /**
     * 钱包余额 actionow:wallet:balance:{walletId}
     */
    public static final String WALLET_BALANCE = PREFIX + "wallet:balance:";

    /**
     * 配额使用 actionow:wallet:quota:{walletId}:{quotaType}
     */
    public static final String WALLET_QUOTA = PREFIX + "wallet:quota:";

    // ==================== 分布式锁 ====================
    /**
     * 分布式锁前缀 actionow:lock:
     */
    public static final String LOCK_PREFIX = PREFIX + "lock:";

    /**
     * 用户注册锁 actionow:lock:user:register:{identifier}
     */
    public static final String LOCK_USER_REGISTER = LOCK_PREFIX + "user:register:";

    /**
     * 支付锁 actionow:lock:payment:{orderId}
     */
    public static final String LOCK_PAYMENT = LOCK_PREFIX + "payment:";

    /**
     * 任务执行锁 actionow:lock:task:{taskId}
     */
    public static final String LOCK_TASK = LOCK_PREFIX + "task:";

    /**
     * 素材生成锁 actionow:lock:asset:generation:{assetId}
     * 防止同一素材并发提交 AI 生成任务
     */
    public static final String LOCK_ASSET_GENERATION = LOCK_PREFIX + "asset:generation:";

    /**
     * 补偿任务锁 actionow:lock:compensation:{compensationTaskId}
     * 防止补偿任务并发执行
     */
    public static final String LOCK_COMPENSATION = LOCK_PREFIX + "compensation:";

    // ==================== 限流 ====================
    /**
     * API限流 actionow:ratelimit:api:{apiKey}:{userId}
     */
    public static final String RATE_LIMIT_API = PREFIX + "ratelimit:api:";

    /**
     * IP限流 actionow:ratelimit:ip:{ip}
     */
    public static final String RATE_LIMIT_IP = PREFIX + "ratelimit:ip:";

    /**
     * 全局限流 actionow:ratelimit:global
     */
    public static final String RATE_LIMIT_GLOBAL = PREFIX + "ratelimit:global";

    /**
     * 用户限流 actionow:ratelimit:user:{userId}
     */
    public static final String RATE_LIMIT_USER = PREFIX + "ratelimit:user:";

    // ==================== Token黑名单 ====================
    /**
     * Token黑名单 actionow:token:blacklist:{jti}
     * 用于存储已注销或已撤销的Token
     */
    public static final String TOKEN_BLACKLIST = PREFIX + "token:blacklist:";

    /**
     * Token黑名单 - 用户维度 actionow:token:blacklist:user:{userId}
     * 用于存储用户所有被撤销的Token时间戳
     */
    public static final String TOKEN_BLACKLIST_USER = PREFIX + "token:blacklist:user:";

    /**
     * Token黑名单 - 会话维度 actionow:token:blacklist:session:{sessionId}
     * 用于存储单个会话被撤销的时间戳，该会话下所有在此时间戳之前签发的Token视为无效
     */
    public static final String TOKEN_BLACKLIST_SESSION = PREFIX + "token:blacklist:session:";

    // ==================== 缓存过期时间（秒） ====================
    /**
     * 用户信息缓存时间 1小时
     */
    public static final long TTL_USER_INFO = 3600L;

    /**
     * 验证码过期时间 5分钟
     */
    public static final long TTL_CAPTCHA = 300L;

    /**
     * 邀请码过期时间 7天
     */
    public static final long TTL_INVITE = 604800L;

    /**
     * OAuth State 过期时间 10分钟
     */
    public static final long TTL_OAUTH_STATE = 600L;
}
