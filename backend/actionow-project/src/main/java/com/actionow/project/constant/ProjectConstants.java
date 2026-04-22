package com.actionow.project.constant;

/**
 * 项目服务常量
 *
 * @author Actionow
 */
public final class ProjectConstants {

    private ProjectConstants() {
    }

    /**
     * 剧本状态
     */
    public static final class ScriptStatus {
        public static final String DRAFT = "DRAFT";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String COMPLETED = "COMPLETED";
        public static final String ARCHIVED = "ARCHIVED";
    }

    /**
     * 分镜状态
     */
    public static final class StoryboardStatus {
        public static final String DRAFT = "DRAFT";
        public static final String GENERATING = "GENERATING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    /**
     * 作用域
     */
    public static final class Scope {
        public static final String SYSTEM = "SYSTEM";
        public static final String WORKSPACE = "WORKSPACE";
        public static final String SCRIPT = "SCRIPT";
    }

    /**
     * 实体类型
     */
    public static final class EntityType {
        public static final String SCRIPT = "SCRIPT";
        public static final String EPISODE = "EPISODE";
        public static final String STORYBOARD = "STORYBOARD";
        public static final String CHARACTER = "CHARACTER";
        public static final String SCENE = "SCENE";
        public static final String PROP = "PROP";
        public static final String STYLE = "STYLE";
        public static final String ASSET = "ASSET";
    }

    /**
     * 实体关系类型
     */
    public static final class RelationType {
        // ==================== 分镜视觉关系 ====================
        /**
         * 分镜发生在场景中 (Storyboard → Scene)
         */
        public static final String TAKES_PLACE_IN = "takes_place_in";

        /**
         * 角色出现在分镜中 (Storyboard → Character)
         * extra_info: {position, positionDetail, action, expression, outfitOverride}
         */
        public static final String APPEARS_IN = "appears_in";

        /**
         * 分镜使用道具 (Storyboard → Prop)
         * extra_info: {position, interaction, state}
         */
        public static final String USES = "uses";

        /**
         * 分镜使用风格 (Storyboard → Style)
         */
        public static final String STYLED_BY = "styled_by";

        // ==================== 分镜音频关系 ====================
        /**
         * 角色在分镜中说话 (Storyboard → Character)
         * extra_info: {dialogueIndex, text, emotion, voiceStyle, timing}
         */
        public static final String SPEAKS_IN = "speaks_in";

        // ==================== 剧集关系 ====================
        /**
         * 剧集包含角色 (Episode → Character)
         */
        public static final String EPISODE_HAS_CHARACTER = "episode_has_character";

        /**
         * 剧集包含场景 (Episode → Scene)
         */
        public static final String EPISODE_HAS_SCENE = "episode_has_scene";

        /**
         * 剧集包含道具 (Episode → Prop)
         */
        public static final String EPISODE_HAS_PROP = "episode_has_prop";

        // ==================== 剧本关系（所有权） ====================
        /**
         * 剧本拥有角色 (Script → Character)
         */
        public static final String HAS_CHARACTER = "has_character";

        /**
         * 剧本拥有场景 (Script → Scene)
         */
        public static final String HAS_SCENE = "has_scene";

        /**
         * 剧本拥有道具 (Script → Prop)
         */
        public static final String HAS_PROP = "has_prop";

        /**
         * 剧本拥有风格 (Script → Style)
         */
        public static final String HAS_STYLE = "has_style";

        // ==================== 角色关系 ====================
        /**
         * 角色间关系 (Character → Character)
         * extra_info: {relationshipType, bidirectional, description}
         */
        public static final String CHARACTER_RELATIONSHIP = "character_relationship";

        // ==================== 角色与道具关系 ====================
        /**
         * 角色装备道具 (Character → Prop)
         * extra_info: {description}
         */
        public static final String EQUIPPED_WITH = "equipped_with";

        /**
         * 角色拥有道具 (Character → Prop)
         * extra_info: {description}
         */
        public static final String OWNS = "owns";

        // ==================== 旧版兼容（已废弃，保留常量避免编译错误） ====================
        @Deprecated
        public static final String RELATIONSHIP = "relationship";
    }

    /**
     * 变更类型
     */
    public static final class ChangeType {
        public static final String CREATED = "CREATED";
        public static final String UPDATED = "UPDATED";
        public static final String DELETED = "DELETED";
    }

    /**
     * 权限类型
     */
    public static final class PermissionType {
        public static final String READ = "READ";
        public static final String WRITE = "WRITE";
        public static final String ADMIN = "ADMIN";
    }

    /**
     * 角色类型
     */
    public static final class CharacterType {
        public static final String PROTAGONIST = "PROTAGONIST";
        public static final String ANTAGONIST = "ANTAGONIST";
        public static final String SUPPORTING = "SUPPORTING";
        public static final String BACKGROUND = "BACKGROUND";
    }

    /**
     * 性别
     */
    public static final class Gender {
        public static final String MALE = "MALE";
        public static final String FEMALE = "FEMALE";
        public static final String OTHER = "OTHER";
    }

    /**
     * 保存模式（版本控制）
     */
    public static final class SaveMode {
        /**
         * 覆盖当前版本（不创建版本快照）
         */
        public static final String OVERWRITE = "OVERWRITE";
        /**
         * 存为新版本（默认，推荐）
         */
        public static final String NEW_VERSION = "NEW_VERSION";
        /**
         * 另存为新实体
         */
        public static final String NEW_ENTITY = "NEW_ENTITY";
    }

    /**
     * 素材关联类型
     */
    public static final class AssetRelationType {
        /**
         * 参考素材 - 创作参考用
         */
        public static final String REFERENCE = "REFERENCE";
        /**
         * 正式素材 - 最终确定使用的素材
         */
        public static final String OFFICIAL = "OFFICIAL";
        /**
         * 草稿素材 - 待定/备选素材
         */
        public static final String DRAFT = "DRAFT";
        /**
         * 音色素材 - 角色声音/场景环境音/道具音效
         */
        public static final String VOICE = "VOICE";
    }

    /**
     * 剧本权限类型
     */
    public static final class ScriptPermissionType {
        public static final String VIEW = "VIEW";
        public static final String EDIT = "EDIT";
        public static final String ADMIN = "ADMIN";

        public static boolean isValid(String type) {
            return VIEW.equals(type) || EDIT.equals(type) || ADMIN.equals(type);
        }

        /** 是否具有查看权限（VIEW ∨ EDIT ∨ ADMIN） */
        public static boolean canView(String type) {
            return VIEW.equals(type) || EDIT.equals(type) || ADMIN.equals(type);
        }

        /** 是否具有编辑权限（EDIT ∨ ADMIN） */
        public static boolean canEdit(String type) {
            return EDIT.equals(type) || ADMIN.equals(type);
        }
    }

    /**
     * 剧本权限授予来源
     */
    public static final class GrantSource {
        public static final String WORKSPACE_ADMIN = "WORKSPACE_ADMIN";
        public static final String SCRIPT_OWNER = "SCRIPT_OWNER";
    }

    /**
     * 素材类型
     */
    public static final class AssetType {
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final String TEXT = "TEXT";
        public static final String DOCUMENT = "DOCUMENT";
        public static final String MODEL = "MODEL";
        public static final String OTHER = "OTHER";
    }
}
