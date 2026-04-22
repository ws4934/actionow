package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Character;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （appearanceData, extraInfo）。
 * 跨 schema 查询（tenant_system）使用 @ResultMap 引用 autoResultMap。
 *
 * @author Actionow
 */
@Mapper
public interface CharacterMapper extends BaseMapper<Character> {

    /**
     * 查询工作空间级角色
     */
    default List<Character> selectWorkspaceCharacters(String workspaceId) {
        return selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getWorkspaceId, workspaceId)
                .eq(Character::getScope, "WORKSPACE")
                .orderByDesc(Character::getUpdatedAt));
    }

    /**
     * 查询剧本级角色
     */
    default List<Character> selectScriptCharacters(String scriptId) {
        return selectList(new LambdaQueryWrapper<Character>()
                .eq(Character::getScriptId, scriptId)
                .eq(Character::getScope, "SCRIPT")
                .orderByDesc(Character::getUpdatedAt));
    }

    /**
     * 查询剧本可用的所有角色（已发布系统级 + 工作空间级 + 剧本级）
     * 跨 schema 查询，使用 @ResultMap 确保 JSONB 字段正确反序列化
     */
    @ResultMap("mybatis-plus_Character")
    @Select("""
            SELECT * FROM (
                SELECT * FROM tenant_system.t_character WHERE scope = 'SYSTEM' AND deleted = 0
                UNION ALL
                SELECT * FROM t_character WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                UNION ALL
                SELECT * FROM t_character WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
            ) combined ORDER BY scope, updated_at DESC
            """)
    List<Character> selectAvailableCharacters(@Param("workspaceId") String workspaceId, @Param("scriptId") String scriptId);

    /**
     * 查询剧本可用的所有角色（带数据库级关键字过滤和数量限制）
     * keyword 条件下推到每个 UNION 分支内，以便利用各自表的索引
     */
    @ResultMap("mybatis-plus_Character")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_character WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_character WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_character WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            ORDER BY scope, updated_at DESC
            <if test="limit != null and limit > 0">
                LIMIT #{limit}
            </if>
            </script>
            """)
    List<Character> selectAvailableCharactersFiltered(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);

    /**
     * 跨 schema 分页查询角色（SYSTEM + WORKSPACE + SCRIPT），支持类型/性别过滤
     * 所有条件均下推到每个 UNION 分支内
     */
    @ResultMap("mybatis-plus_Character")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_character WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
                UNION ALL
                SELECT * FROM t_character WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
                UNION ALL
                SELECT * FROM t_character WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
            ) combined
            ORDER BY scope, updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Character> selectAvailableCharactersPaginated(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("characterType") String characterType,
            @Param("gender") String gender,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 跨 schema 统计角色总数（SYSTEM + WORKSPACE + SCRIPT），支持类型/性别过滤
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT id FROM tenant_system.t_character WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
                UNION ALL
                SELECT id FROM t_character WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
                UNION ALL
                SELECT id FROM t_character WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                <if test="characterType != null and characterType != ''">
                    AND character_type = #{characterType}
                </if>
                <if test="gender != null and gender != ''">
                    AND gender = #{gender}
                </if>
            ) combined
            </script>
            """)
    long countAvailableCharacters(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("characterType") String characterType,
            @Param("gender") String gender);

    /**
     * 查询已发布的系统级角色（公共库），供普通租户浏览
     */
    @ResultMap("mybatis-plus_Character")
    @Select("SELECT * FROM tenant_system.t_character WHERE scope = 'SYSTEM' AND deleted = 0 ORDER BY published_at DESC")
    List<Character> selectSystemCharacters();

    /**
     * 查询系统租户中所有角色（含草稿 scope=WORKSPACE，供系统管理员管理）
     */
    @ResultMap("mybatis-plus_Character")
    @Select("SELECT * FROM tenant_system.t_character WHERE deleted = 0 ORDER BY created_at DESC")
    List<Character> selectSystemCharacterDrafts();

    /**
     * 发布角色到公共库：将 scope 从 WORKSPACE 升级为 SYSTEM
     */
    @Update("""
            UPDATE tenant_system.t_character
            SET scope = 'SYSTEM', published_at = #{publishedAt}, published_by = #{publishedBy},
                publish_note = #{publishNote}, updated_at = CURRENT_TIMESTAMP, updated_by = #{publishedBy}
            WHERE id = #{id} AND deleted = 0
            """)
    int publishCharacter(@Param("id") String id,
                         @Param("publishedAt") LocalDateTime publishedAt,
                         @Param("publishedBy") String publishedBy,
                         @Param("publishNote") String publishNote);

    /**
     * 下架角色：将 scope 从 SYSTEM 降回 WORKSPACE
     */
    @Update("""
            UPDATE tenant_system.t_character
            SET scope = 'WORKSPACE', published_at = NULL, published_by = NULL, publish_note = NULL,
                updated_at = CURRENT_TIMESTAMP, updated_by = #{operatorId}
            WHERE id = #{id} AND deleted = 0
            """)
    int unpublishCharacter(@Param("id") String id, @Param("operatorId") String operatorId);

    /**
     * 从 tenant_system 查询单个角色（供系统管理员操作时使用）
     */
    @ResultMap("mybatis-plus_Character")
    @Select("SELECT * FROM tenant_system.t_character WHERE id = #{id} AND deleted = 0")
    Character selectSystemCharacterById(@Param("id") String id);
}
