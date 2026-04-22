package com.actionow.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用响应码枚举
 * 格式: {SERVICE}{MODULE}{CODE}
 * - SERVICE: 00=GATEWAY, 01=USER, 02=WORKSPACE, 03=WALLET, 04=SCRIPT, 05=ASSET, 06=AI, 07=TASK, 08=COLLAB
 * - MODULE: 00=COMMON, 01=AUTH, 02=CRUD, 03=BUSINESS
 * - CODE: 001-099=参数错误, 100-199=认证授权, 200-299=业务逻辑, 300-399=外部服务, 900-999=系统错误
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements IResultCode {

    // ==================== 成功 ====================
    SUCCESS("0", "操作成功"),

    // ==================== 通用错误 (00) ====================
    FAIL("0000001", "操作失败"),
    PARAM_MISSING("0000010", "缺少必要参数"),
    PARAM_INVALID("0000002", "参数格式错误"),
    PARAM_OUT_OF_RANGE("0000003", "参数超出范围"),

    // 认证授权错误
    UNAUTHORIZED("0001100", "未认证，请先登录"),
    TOKEN_EXPIRED("0001101", "Token已过期"),
    TOKEN_INVALID("0001102", "Token无效"),
    FORBIDDEN("0001103", "无权限访问"),

    // 资源错误
    NOT_FOUND("0002200", "资源不存在"),
    ALREADY_EXISTS("0002201", "资源已存在"),
    CONFLICT("0002202", "资源冲突"),

    // 系统错误
    INTERNAL_ERROR("0000900", "系统内部错误"),
    SERVICE_UNAVAILABLE("0000901", "服务不可用"),
    TIMEOUT("0000902", "请求超时"),
    RATE_LIMITED("0000903", "请求过于频繁，请稍后重试"),

    // ==================== 用户服务错误 (01) ====================
    USER_NOT_FOUND("0102200", "用户不存在"),
    USERNAME_EXISTS("0102201", "用户名已存在"),
    EMAIL_EXISTS("0102202", "邮箱已注册"),
    PHONE_EXISTS("0102203", "手机号已注册"),
    PASSWORD_INCORRECT("0101100", "密码错误"),
    VERIFY_CODE_ERROR("0101101", "验证码错误或已过期"),
    ACCOUNT_DISABLED("0101102", "账号已被禁用"),
    LOGIN_FAILED_LIMIT("0101103", "登录失败次数过多，请稍后再试"),
    OAUTH_BINDINGFAILED("0103200", "OAuth绑定失败"),
    OAUTH_BINDINGEXISTS("0103201", "该第三方账号已绑定其他用户"),
    CANNOT_UNBIND_LAST("0103202", "无法解绑最后一种登录方式"),

    // 邀请码错误
    INVITATION_CODE_REQUIRED("0103210", "注册需要邀请码"),
    INVITATION_CODE_INVALID("0103211", "邀请码无效"),
    INVITATION_CODE_EXPIRED("0103212", "邀请码已过期"),
    INVITATION_CODE_EXHAUSTED("0103213", "邀请码已用完"),
    INVITATION_CODE_DISABLED("0103214", "邀请码已禁用"),
    INVITATION_CODE_NOT_ACTIVE("0103215", "邀请码尚未生效"),
    USER_INVITATION_CODE_NOT_ALLOWED("0103216", "不支持使用用户邀请码注册"),
    INVITATION_CODE_NOT_FOUND("0103217", "邀请码不存在"),

    // ==================== 工作空间服务错误 (02) ====================
    WORKSPACE_NOT_FOUND("0202200", "工作空间不存在"),
    WORKSPACE_NAME_EXISTS("0202201", "工作空间名称已存在"),
    NOT_WORKSPACE_MEMBER("0201103", "您不是该工作空间的成员"),
    ALREADY_WORKSPACE_MEMBER("0202202", "已经是该工作空间的成员"),
    WORKSPACE_MEMBER_LIMIT("0203200", "工作空间成员数量已达上限"),
    CANNOT_REMOVE_CREATOR("0203201", "无法移除工作空间创建者"),
    CREATOR_CANNOT_LEAVE("0203202", "创建者不能直接退出，请先转让所有权"),
    INVALID_ROLE("0200001", "无效的成员角色"),
    NO_PERMISSION("0201104", "没有操作权限"),
    INVITATION_INVALID("0203203", "邀请链接无效或不存在"),
    INVITATION_EXPIRED("0203204", "邀请链接已过期"),
    INVITATION_USED_UP("0203205", "邀请链接已达到使用次数上限"),
    INVITATION_EMAIL_MISMATCH("0203206", "该邀请仅限指定邮箱用户使用"),
    CANNOT_INVITE_AS_CREATOR("0203207", "不能邀请为创建者角色"),

    // ==================== 钱包服务错误 (03) ====================
    WALLET_NOT_FOUND("0302200", "钱包不存在"),
    WALLET_BALANCE_NOT_ENOUGH("0303200", "余额不足"),
    WALLET_FROZEN("0303201", "钱包已冻结"),
    QUOTA_EXCEEDED("0303202", "配额超限"),
    TRANSACTION_FAILED("0303203", "交易失败"),
    FREEZE_RECORD_NOT_FOUND("0303204", "冻结记录不存在"),

    // ==================== 剧本服务错误 (04) ====================
    SCRIPT_NOT_FOUND("0402200", "剧本不存在"),
    EPISODE_NOT_FOUND("0402201", "剧集不存在"),
    STORYBOARD_NOT_FOUND("0402202", "分镜不存在"),
    CHARACTER_NOT_FOUND("0402203", "角色不存在"),
    ELEMENT_NOT_FOUND("0402204", "创作元素不存在"),
    SCENE_NOT_FOUND("0402205", "场景不存在"),
    STYLE_NOT_FOUND("0402206", "风格不存在"),
    PROP_NOT_FOUND("0402207", "道具不存在"),
    RELATION_NOT_FOUND("0402208", "关系不存在"),
    RELATION_ALREADY_EXISTS("0402209", "关系已存在"),
    CANVAS_NOT_FOUND("0402210", "画布布局不存在"),
    SCRIPT_PERMISSION_NOT_FOUND("0402211", "剧本权限不存在"),
    SCRIPT_PERMISSION_EXISTS("0402212", "剧本权限已存在"),
    SCRIPT_NO_PERMISSION("0401104", "无权访问该剧本"),
    LIBRARY_RESOURCE_NOT_FOUND("0402220", "公共库资源不存在"),
    RESOURCE_ALREADY_PUBLISHED("0402221", "资源已发布，无需重复发布"),
    RESOURCE_NOT_PUBLISHED("0402222", "资源尚未发布"),

    // ==================== 素材服务错误 (05) ====================
    ASSET_NOT_FOUND("0502200", "素材不存在"),
    ASSET_HAS_REFERENCES("0502201", "素材仍被引用，无法永久删除"),
    FILE_TYPE_NOT_ALLOWED("0500001", "不支持的文件类型"),
    FILE_SIZE_EXCEEDED("0500002", "文件大小超出限制"),
    UPLOAD_FAILED("0503300", "文件上传失败"),

    // ==================== AI服务错误 (06) ====================
    MODEL_UNAVAILABLE("0603300", "AI模型不可用"),
    CONTENT_VIOLATION("0603200", "内容违规"),
    GENERATION_FAILED("0603201", "生成失败"),
    PROMPT_TEMPLATE_NOT_FOUND("0602200", "提示词模板不存在"),
    WORKFLOW_NOT_FOUND("0602201", "工作流不存在"),
    WORKFLOW_DISABLED("0603202", "工作流已禁用"),
    WORKFLOW_SYNC_FAILED("0603301", "工作流同步失败"),
    WORKFLOW_EXECUTION_FAILED("0603302", "工作流执行失败"),
    DIFY_CONNECTION_FAILED("0603303", "DIFY服务连接失败"),
    INVALID_WORKFLOW_TYPE("0600001", "无效的工作流类型"),

    // ==================== 任务服务错误 (07) ====================
    TASK_NOT_FOUND("0702200", "任务不存在"),
    TASK_ALREADY_RUNNING("0703200", "任务正在执行中"),
    TASK_CANCELLED("0703201", "任务已取消"),
    TASK_TIMEOUT("0703202", "任务执行超时"),
    CONCURRENT_OPERATION("0703203", "并发操作冲突，请稍后重试"),

    // ==================== 剧本权限错误 (04 扩展) ====================
    SCRIPT_CREATE_NOT_ALLOWED("0403200", "当前租户不允许成员创建剧本"),
    CANNOT_REMOVE_SCRIPT_OWNER("0403201", "不能移除剧本创建者的权限");

    private final String code;
    private final String message;
}
