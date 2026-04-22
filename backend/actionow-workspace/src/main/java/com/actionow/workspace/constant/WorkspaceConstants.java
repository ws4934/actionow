package com.actionow.workspace.constant;

/**
 * 工作空间常量
 *
 * @author Actionow
 */
public final class WorkspaceConstants {

    private WorkspaceConstants() {
    }

    /**
     * 默认成员上限 (Free 计划)
     */
    public static final int DEFAULT_MEMBER_LIMIT = 5;

    /**
     * 邀请码长度
     */
    public static final int INVITATION_CODE_LENGTH = 8;

    /**
     * 单用户最大工作空间数量
     */
    public static final int MAX_WORKSPACES_PER_USER = 10;

    /**
     * 角色定义
     * Creator: 创建者，最高权限，可删除空间、转让所有权
     * Admin: 管理员，管理成员、管理配置，不能删除空间
     * Member: 普通成员，创建/编辑内容，不能管理成员
     * Guest: 访客，只读权限，不能创建内容
     */
    public static final class Role {
        public static final String CREATOR = "CREATOR";
        public static final String ADMIN = "ADMIN";
        public static final String MEMBER = "MEMBER";
        public static final String GUEST = "GUEST";

        /**
         * 角色优先级（用于权限比较）
         */
        public static int getPriority(String role) {
            return switch (role) {
                case CREATOR -> 100;
                case ADMIN -> 80;
                case MEMBER -> 60;
                case GUEST -> 40;
                default -> 0;
            };
        }

        /**
         * 检查是否是有效角色
         */
        public static boolean isValid(String role) {
            return CREATOR.equals(role) || ADMIN.equals(role)
                    || MEMBER.equals(role) || GUEST.equals(role);
        }

        /**
         * 检查是否可以管理成员（邀请、移除、修改角色）
         */
        public static boolean canManageMembers(String role) {
            return CREATOR.equals(role) || ADMIN.equals(role);
        }

        /**
         * 检查是否可以创建/编辑内容（剧本、分镜等）
         */
        public static boolean canEditContent(String role) {
            return CREATOR.equals(role) || ADMIN.equals(role) || MEMBER.equals(role);
        }

        /**
         * 检查是否可以修改空间设置
         */
        public static boolean canModifyWorkspace(String role) {
            return CREATOR.equals(role) || ADMIN.equals(role);
        }

        /**
         * 检查是否可以删除空间
         */
        public static boolean canDeleteWorkspace(String role) {
            return CREATOR.equals(role);
        }
    }

    /**
     * 工作空间状态
     */
    public static final class Status {
        public static final String ACTIVE = "ACTIVE";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String DELETED = "DELETED";
    }

    /**
     * 成员状态
     */
    public static final class MemberStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final String INVITED = "INVITED";
    }

    /**
     * 订阅计划类型
     */
    public static final class PlanType {
        public static final String FREE = "Free";
        public static final String BASIC = "Basic";
        public static final String PRO = "Pro";
        public static final String ENTERPRISE = "Enterprise";

        /**
         * 根据计划类型获取最大成员数
         */
        public static int getMaxMembers(String planType) {
            return switch (planType) {
                case FREE -> 5;
                case BASIC -> 20;
                case PRO -> 50;
                case ENTERPRISE -> Integer.MAX_VALUE;
                default -> 5;
            };
        }

        /**
         * 检查计划类型是否有效
         */
        public static boolean isValid(String planType) {
            return FREE.equals(planType) || BASIC.equals(planType)
                    || PRO.equals(planType) || ENTERPRISE.equals(planType);
        }
    }
}
