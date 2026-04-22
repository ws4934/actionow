package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Scene;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface SceneMapper extends BaseMapper<Scene> {

    @Select("SELECT * FROM t_scene WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0 ORDER BY updated_at DESC")
    List<Scene> selectWorkspaceScenes(@Param("workspaceId") String workspaceId);

    @Select("SELECT * FROM t_scene WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0 ORDER BY updated_at DESC")
    List<Scene> selectScriptScenes(@Param("scriptId") String scriptId);

    @Select("""
            SELECT * FROM (
                SELECT * FROM tenant_system.t_scene WHERE scope = 'SYSTEM' AND deleted = 0
                UNION ALL
                SELECT * FROM t_scene WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                UNION ALL
                SELECT * FROM t_scene WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
            ) combined ORDER BY scope, updated_at DESC
            """)
    List<Scene> selectAvailableScenes(@Param("workspaceId") String workspaceId, @Param("scriptId") String scriptId);

    /**
     * 查询剧本可用的所有场景（带数据库级关键字过滤和数量限制）
     * keyword 条件下推到每个 UNION 分支内，以便利用各自表的索引
     */
    @ResultMap("mybatis-plus_Scene")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_scene WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_scene WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_scene WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
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
    List<Scene> selectAvailableScenesFiltered(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);

    /**
     * 跨 schema 分页查询场景（SYSTEM + WORKSPACE + SCRIPT）
     * 所有条件均下推到每个 UNION 分支内
     */
    @ResultMap("mybatis-plus_Scene")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_scene WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_scene WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_scene WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            ORDER BY scope, updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Scene> selectAvailableScenesPaginated(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 跨 schema 统计场景总数（SYSTEM + WORKSPACE + SCRIPT）
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT id FROM tenant_system.t_scene WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT id FROM t_scene WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT id FROM t_scene WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR appearance_data::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            </script>
            """)
    long countAvailableScenes(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword);

    @Select("SELECT * FROM tenant_system.t_scene WHERE scope = 'SYSTEM' AND deleted = 0 ORDER BY published_at DESC")
    List<Scene> selectSystemScenes();

    @Select("SELECT * FROM tenant_system.t_scene WHERE deleted = 0 ORDER BY created_at DESC")
    List<Scene> selectSystemSceneDrafts();

    @Update("""
            UPDATE tenant_system.t_scene
            SET scope = 'SYSTEM', published_at = #{publishedAt}, published_by = #{publishedBy},
                publish_note = #{publishNote}, updated_at = CURRENT_TIMESTAMP, updated_by = #{publishedBy}
            WHERE id = #{id} AND deleted = 0
            """)
    int publishScene(@Param("id") String id,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("publishedBy") String publishedBy,
                     @Param("publishNote") String publishNote);

    @Update("""
            UPDATE tenant_system.t_scene
            SET scope = 'WORKSPACE', published_at = NULL, published_by = NULL, publish_note = NULL,
                updated_at = CURRENT_TIMESTAMP, updated_by = #{operatorId}
            WHERE id = #{id} AND deleted = 0
            """)
    int unpublishScene(@Param("id") String id, @Param("operatorId") String operatorId);

    @Select("SELECT * FROM tenant_system.t_scene WHERE id = #{id} AND deleted = 0")
    Scene selectSystemSceneById(@Param("id") String id);
}
