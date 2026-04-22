package com.actionow.canvas.constant;

import java.util.Map;
import java.util.Set;

/**
 * Canvas 服务常量
 * 定义视图键、实体类型、层级、关系类型及边规则
 *
 * 统一主画布模型：1 Script = 1 Canvas，通过视图筛选显示不同实体类型
 *
 * @author Actionow
 */
public final class CanvasConstants {

    private CanvasConstants() {
    }

    /**
     * 视图键（ViewKey）
     * 每个视图定义了可见的实体类型和筛选规则
     * 与旧的 Dimension 概念对应，但现在是视图的标识而非独立画布
     */
    public static final class ViewKey {
        public static final String SCRIPT = "SCRIPT";
        public static final String EPISODE = "EPISODE";
        public static final String STORYBOARD = "STORYBOARD";
        public static final String CHARACTER = "CHARACTER";
        public static final String SCENE = "SCENE";
        public static final String PROP = "PROP";
        public static final String ASSET = "ASSET";

        public static final Set<String> ALL = Set.of(
                SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET
        );

        public static boolean isValid(String viewKey) {
            return ALL.contains(viewKey);
        }
    }

    /**
     * 视图类型
     */
    public static final class ViewType {
        /** 预设视图 - 系统自动创建的7种标准视图 */
        public static final String PRESET = "PRESET";
        /** 自定义视图 - 用户创建的自定义筛选视图 */
        public static final String CUSTOM = "CUSTOM";
    }

    /**
     * 节点层级
     * 用于在画布中组织节点的层级关系，与 EntityType 一一对应
     */
    public static final class Layer {
        public static final String SCRIPT = "SCRIPT";
        public static final String EPISODE = "EPISODE";
        public static final String STORYBOARD = "STORYBOARD";
        public static final String CHARACTER = "CHARACTER";
        public static final String SCENE = "SCENE";
        public static final String PROP = "PROP";
        public static final String STYLE = "STYLE";
        public static final String ASSET = "ASSET";

        /**
         * 根据实体类型推断层级
         * 素材子类型统一归为 ASSET 层
         */
        public static String fromEntityType(String entityType) {
            if (EntityType.isAssetSubType(entityType)) {
                return ASSET;
            }
            return entityType;
        }
    }

    /**
     * 实体类型
     * 画布可以展示的所有实体类型
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

        // 素材子类型 - 前端使用这些类型作为实体类型
        public static final String TEXT = "text";
        public static final String IMAGE = "image";
        public static final String VIDEO = "video";
        public static final String AUDIO = "audio";
        public static final String DOCUMENT = "document";

        // 素材子类型集合
        public static final Set<String> ASSET_SUB_TYPES = Set.of(TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT);

        /**
         * 判断是否为素材子类型
         */
        public static boolean isAssetSubType(String type) {
            return ASSET_SUB_TYPES.contains(type);
        }

        /**
         * 归一化实体类型 - 将素材子类型归一化为 ASSET
         * 用于 Feign 调用获取实体信息时
         */
        public static String normalize(String type) {
            return isAssetSubType(type) ? ASSET : type;
        }

        /**
         * 判断是否为素材类型（包括 ASSET 和所有子类型）
         */
        public static boolean isAssetType(String type) {
            return ASSET.equals(type) || isAssetSubType(type);
        }
    }

    /**
     * 关系类型
     */
    public static final class RelationType {
        // 包含关系
        public static final String HAS_EPISODE = "has_episode";
        public static final String HAS_STORYBOARD = "has_storyboard";
        public static final String HAS_CHARACTER = "has_character";
        public static final String HAS_SCENE = "has_scene";
        public static final String HAS_PROP = "has_prop";
        public static final String HAS_STYLE = "has_style";
        public static final String HAS_ASSET = "has_asset";

        // 分镜相关关系
        public static final String APPEARS_IN = "appears_in";
        public static final String TAKES_PLACE_IN = "takes_place_in";
        public static final String USES = "uses";
        public static final String STYLED_BY = "styled_by";

        // 角色间关系
        public static final String RELATIONSHIP = "relationship";

        // 通用关联
        public static final String RELATES_TO = "relates_to";

        /**
         * 获取关系显示标签
         */
        public static String getLabel(String relationType) {
            return switch (relationType) {
                case HAS_EPISODE -> "包含剧集";
                case HAS_STORYBOARD -> "包含分镜";
                case HAS_CHARACTER -> "拥有角色";
                case HAS_SCENE -> "拥有场景";
                case HAS_PROP -> "拥有道具";
                case HAS_STYLE -> "拥有风格";
                case HAS_ASSET -> "关联素材";
                case APPEARS_IN -> "出现于";
                case TAKES_PLACE_IN -> "发生于";
                case USES -> "使用";
                case STYLED_BY -> "应用风格";
                case RELATIONSHIP -> "关系";
                default -> "关联";
            };
        }
    }

    /**
     * 每个视图允许的节点类型（可见实体类型）
     * 用于筛选显示特定视图下的节点
     */
    public static final class VisibleEntityTypes {
        // SCRIPT 视图：显示所有实体类型
        public static final Set<String> SCRIPT_VIEW = Set.of(
                EntityType.SCRIPT,
                EntityType.EPISODE,
                EntityType.STORYBOARD,
                EntityType.CHARACTER,
                EntityType.SCENE,
                EntityType.PROP,
                EntityType.ASSET
        );

        // EPISODE 视图：从剧集开始往下
        public static final Set<String> EPISODE_VIEW = Set.of(
                EntityType.EPISODE,
                EntityType.STORYBOARD,
                EntityType.CHARACTER,
                EntityType.SCENE,
                EntityType.PROP,
                EntityType.ASSET
        );

        // STORYBOARD 视图：分镜及其关联元素
        public static final Set<String> STORYBOARD_VIEW = Set.of(
                EntityType.STORYBOARD,
                EntityType.CHARACTER,
                EntityType.SCENE,
                EntityType.PROP,
                EntityType.STYLE,
                EntityType.ASSET
        );

        // CHARACTER 视图：角色及关联素材
        public static final Set<String> CHARACTER_VIEW = Set.of(
                EntityType.CHARACTER,
                EntityType.ASSET
        );

        // SCENE 视图：场景及关联素材
        public static final Set<String> SCENE_VIEW = Set.of(
                EntityType.SCENE,
                EntityType.ASSET
        );

        // PROP 视图：道具及关联素材
        public static final Set<String> PROP_VIEW = Set.of(
                EntityType.PROP,
                EntityType.ASSET
        );

        // ASSET 视图：仅素材
        public static final Set<String> ASSET_VIEW = Set.of(
                EntityType.ASSET
        );

        /**
         * 根据视图键获取可见实体类型
         */
        public static Set<String> getByViewKey(String viewKey) {
            return switch (viewKey) {
                case ViewKey.SCRIPT -> SCRIPT_VIEW;
                case ViewKey.EPISODE -> EPISODE_VIEW;
                case ViewKey.STORYBOARD -> STORYBOARD_VIEW;
                case ViewKey.CHARACTER -> CHARACTER_VIEW;
                case ViewKey.SCENE -> SCENE_VIEW;
                case ViewKey.PROP -> PROP_VIEW;
                case ViewKey.ASSET -> ASSET_VIEW;
                default -> Set.of();
            };
        }

        /**
         * 检查实体类型在指定视图是否可见
         * 素材子类型（text, image, video, audio, document）视为 ASSET 类型
         */
        public static boolean isVisible(String viewKey, String entityType) {
            String normalizedType = EntityType.normalize(entityType);
            return getByViewKey(viewKey).contains(normalizedType);
        }

        /**
         * 获取视图的根实体类型
         */
        public static String getRootEntityType(String viewKey) {
            return viewKey; // 视图键与根实体类型一致
        }
    }

    /**
     * @deprecated 使用 VisibleEntityTypes 替代
     * 每个维度允许的节点类型 - 保留用于向后兼容
     */
    @Deprecated
    public static final class AllowedNodes {
        public static final Set<String> SCRIPT_DIMENSION = VisibleEntityTypes.SCRIPT_VIEW;
        public static final Set<String> EPISODE_DIMENSION = VisibleEntityTypes.EPISODE_VIEW;
        public static final Set<String> STORYBOARD_DIMENSION = VisibleEntityTypes.STORYBOARD_VIEW;
        public static final Set<String> CHARACTER_DIMENSION = VisibleEntityTypes.CHARACTER_VIEW;
        public static final Set<String> SCENE_DIMENSION = VisibleEntityTypes.SCENE_VIEW;
        public static final Set<String> PROP_DIMENSION = VisibleEntityTypes.PROP_VIEW;
        public static final Set<String> ASSET_DIMENSION = VisibleEntityTypes.ASSET_VIEW;

        public static Set<String> getByDimension(String dimension) {
            return VisibleEntityTypes.getByViewKey(dimension);
        }

        public static boolean isAllowed(String dimension, String entityType) {
            return VisibleEntityTypes.isVisible(dimension, entityType);
        }
    }

    /**
     * 边规则：定义每个视图中允许的源→目标边类型
     */
    public static final class EdgeRules {
        // SCRIPT 视图边规则
        public static final Map<String, Set<String>> SCRIPT_VIEW_RULES = Map.of(
                EntityType.SCRIPT, Set.of(EntityType.EPISODE, EntityType.CHARACTER, EntityType.SCENE, EntityType.PROP, EntityType.ASSET),
                EntityType.EPISODE, Set.of(EntityType.CHARACTER, EntityType.SCENE, EntityType.PROP, EntityType.ASSET),
                EntityType.CHARACTER, Set.of(EntityType.ASSET),
                EntityType.SCENE, Set.of(EntityType.ASSET),
                EntityType.PROP, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // EPISODE 视图边规则
        public static final Map<String, Set<String>> EPISODE_VIEW_RULES = Map.of(
                EntityType.EPISODE, Set.of(EntityType.STORYBOARD, EntityType.CHARACTER, EntityType.SCENE, EntityType.PROP, EntityType.ASSET),
                EntityType.STORYBOARD, Set.of(EntityType.CHARACTER, EntityType.SCENE, EntityType.PROP, EntityType.ASSET),
                EntityType.CHARACTER, Set.of(EntityType.ASSET),
                EntityType.SCENE, Set.of(EntityType.ASSET),
                EntityType.PROP, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // STORYBOARD 视图边规则
        public static final Map<String, Set<String>> STORYBOARD_VIEW_RULES = Map.of(
                EntityType.STORYBOARD, Set.of(EntityType.CHARACTER, EntityType.SCENE, EntityType.PROP, EntityType.STYLE, EntityType.ASSET),
                EntityType.CHARACTER, Set.of(EntityType.ASSET),
                EntityType.SCENE, Set.of(EntityType.ASSET),
                EntityType.PROP, Set.of(EntityType.ASSET),
                EntityType.STYLE, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // CHARACTER 视图边规则
        public static final Map<String, Set<String>> CHARACTER_VIEW_RULES = Map.of(
                EntityType.CHARACTER, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // SCENE 视图边规则
        public static final Map<String, Set<String>> SCENE_VIEW_RULES = Map.of(
                EntityType.SCENE, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // PROP 视图边规则
        public static final Map<String, Set<String>> PROP_VIEW_RULES = Map.of(
                EntityType.PROP, Set.of(EntityType.ASSET),
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        // ASSET 视图边规则
        public static final Map<String, Set<String>> ASSET_VIEW_RULES = Map.of(
                EntityType.ASSET, Set.of(EntityType.ASSET)
        );

        /**
         * 根据视图键获取边规则
         */
        public static Map<String, Set<String>> getByViewKey(String viewKey) {
            return switch (viewKey) {
                case ViewKey.SCRIPT -> SCRIPT_VIEW_RULES;
                case ViewKey.EPISODE -> EPISODE_VIEW_RULES;
                case ViewKey.STORYBOARD -> STORYBOARD_VIEW_RULES;
                case ViewKey.CHARACTER -> CHARACTER_VIEW_RULES;
                case ViewKey.SCENE -> SCENE_VIEW_RULES;
                case ViewKey.PROP -> PROP_VIEW_RULES;
                case ViewKey.ASSET -> ASSET_VIEW_RULES;
                default -> Map.of();
            };
        }

        /**
         * @deprecated 使用 getByViewKey 替代
         */
        @Deprecated
        public static Map<String, Set<String>> getByDimension(String dimension) {
            return getByViewKey(dimension);
        }

        /**
         * 检查边是否在指定视图允许
         * 素材子类型归一化为 ASSET
         */
        public static boolean isEdgeAllowed(String viewKey, String sourceType, String targetType) {
            String normalizedSource = EntityType.normalize(sourceType);
            String normalizedTarget = EntityType.normalize(targetType);
            Map<String, Set<String>> rules = getByViewKey(viewKey);
            Set<String> allowedTargets = rules.get(normalizedSource);
            return allowedTargets != null && allowedTargets.contains(normalizedTarget);
        }

        /**
         * 根据源和目标类型推断关系类型
         * 素材子类型归一化为 ASSET
         */
        public static String inferRelationType(String sourceType, String targetType) {
            String normalizedSource = EntityType.normalize(sourceType);
            String normalizedTarget = EntityType.normalize(targetType);
            if (EntityType.SCRIPT.equals(normalizedSource)) {
                return switch (normalizedTarget) {
                    case EntityType.EPISODE -> RelationType.HAS_EPISODE;
                    case EntityType.CHARACTER -> RelationType.HAS_CHARACTER;
                    case EntityType.SCENE -> RelationType.HAS_SCENE;
                    case EntityType.PROP -> RelationType.HAS_PROP;
                    case EntityType.ASSET -> RelationType.HAS_ASSET;
                    default -> RelationType.RELATES_TO;
                };
            }
            if (EntityType.EPISODE.equals(normalizedSource)) {
                return switch (normalizedTarget) {
                    case EntityType.STORYBOARD -> RelationType.HAS_STORYBOARD;
                    case EntityType.CHARACTER -> RelationType.HAS_CHARACTER;
                    case EntityType.SCENE -> RelationType.HAS_SCENE;
                    case EntityType.PROP -> RelationType.HAS_PROP;
                    case EntityType.ASSET -> RelationType.HAS_ASSET;
                    default -> RelationType.RELATES_TO;
                };
            }
            if (EntityType.STORYBOARD.equals(normalizedSource)) {
                return switch (normalizedTarget) {
                    case EntityType.CHARACTER -> RelationType.APPEARS_IN;
                    case EntityType.SCENE -> RelationType.TAKES_PLACE_IN;
                    case EntityType.PROP -> RelationType.USES;
                    case EntityType.STYLE -> RelationType.STYLED_BY;
                    case EntityType.ASSET -> RelationType.HAS_ASSET;
                    default -> RelationType.RELATES_TO;
                };
            }
            if (EntityType.ASSET.equals(normalizedTarget)) {
                return RelationType.HAS_ASSET;
            }
            return RelationType.RELATES_TO;
        }
    }

    /**
     * 线条样式
     */
    public static final class LineStyle {
        public static final String SOLID = "solid";
        public static final String DASHED = "dashed";
        public static final String DOTTED = "dotted";
    }

    /**
     * 路径类型
     */
    public static final class PathType {
        public static final String STRAIGHT = "straight";
        public static final String BEZIER = "bezier";
        public static final String STEP = "step";
        public static final String SMOOTHSTEP = "smoothstep";
    }

    /**
     * 连接点位置
     */
    public static final class HandlePosition {
        public static final String TOP = "top";
        public static final String BOTTOM = "bottom";
        public static final String LEFT = "left";
        public static final String RIGHT = "right";
    }

    /**
     * 布局策略
     */
    public static final class LayoutStrategy {
        public static final String GRID = "GRID";
        public static final String TREE = "TREE";
        public static final String FORCE = "FORCE";
    }

    /**
     * 变更类型（用于 MQ 消息）
     */
    public static final class ChangeType {
        public static final String CREATED = "CREATED";
        public static final String UPDATED = "UPDATED";
        public static final String DELETED = "DELETED";
    }

    /**
     * 布局参数默认值
     */
    public static final class LayoutDefaults {
        public static final int NODE_WIDTH = 200;
        public static final int NODE_HEIGHT = 150;
        public static final int GAP_X = 50;
        public static final int GAP_Y = 50;
        public static final int COLUMNS = 4;
    }

    /**
     * 默认线条样式
     */
    public static final class DefaultLineStyle {
        public static final String STROKE_COLOR = "#666";
        public static final int STROKE_WIDTH = 2;
        public static final String STROKE_STYLE = LineStyle.SOLID;
        public static final boolean ANIMATED = false;
    }
}
