package com.actionow.workspace.enums;

import com.actionow.common.core.result.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作空间服务错误码枚举
 * 格式: 30xxx（工作空间服务错误码段）
 * - 300xx: 工作空间相关
 * - 301xx: 成员相关
 * - 302xx: 邀请相关
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum WorkspaceErrorCode implements IResultCode {

    // ==================== 工作空间相关 (300xx) ====================
    WORKSPACE_NOT_FOUND("30001", "工作空间不存在"),
    NO_PERMISSION("30002", "无权访问该工作空间"),
    WORKSPACE_NAME_EXISTS("30003", "工作空间名称已存在"),
    SLUG_EXISTS("30004", "该标识(slug)已被占用"),
    WORKSPACE_LIMIT_EXCEEDED("30005", "已达到最大空间数量限制"),
    WORKSPACE_DELETED("30013", "工作空间已被删除"),
    WORKSPACE_SUSPENDED("30014", "工作空间已被暂停"),

    // ==================== 成员相关 (301xx) ====================
    MEMBER_LIMIT_EXCEEDED("30006", "成员数量已达上限"),
    ALREADY_MEMBER("30007", "用户已是该空间成员"),
    NOT_MEMBER("30009", "您不是该工作空间的成员"),
    CREATOR_CANNOT_LEAVE("30010", "创建者不能退出空间，请先转让所有权"),
    CANNOT_MODIFY_CREATOR("30011", "不能修改创建者角色"),
    CANNOT_REMOVE_CREATOR("30012", "不能移除工作空间创建者"),
    INVALID_ROLE("30015", "无效的成员角色"),
    ROLE_PRIORITY_ERROR("30016", "无法分配高于自己的角色"),

    // ==================== 邀请相关 (302xx) ====================
    INVITATION_INVALID("30008", "邀请链接无效或不存在"),
    INVITATION_EXPIRED("30017", "邀请链接已过期"),
    INVITATION_USED_UP("30018", "邀请链接已达到使用次数上限"),
    INVITATION_EMAIL_MISMATCH("30019", "该邀请仅限指定邮箱用户使用"),
    CANNOT_INVITE_AS_CREATOR("30020", "不能邀请为创建者角色"),
    INVALID_PLAN_TYPE("30021", "无效的订阅计划类型");

    private final String code;
    private final String message;
}
