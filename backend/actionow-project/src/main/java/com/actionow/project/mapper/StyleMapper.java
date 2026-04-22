package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Style;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风格 Mapper
 * 跨 schema 查询（tenant_system）使用 @ResultMap 引用 autoResultMap，
 * 确保 JSONB 字段（styleParams, extraInfo）正确反序列化。
 *
 * @author Actionow
 */
@Mapper
public interface StyleMapper extends BaseMapper<Style> {

    @Select("SELECT * FROM t_style WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0 ORDER BY updated_at DESC")
    List<Style> selectWorkspaceStyles(@Param("workspaceId") String workspaceId);

    @Select("SELECT * FROM t_style WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0 ORDER BY updated_at DESC")
    List<Style> selectScriptStyles(@Param("scriptId") String scriptId);

    /**
     * 查询剧本可用的所有风格（已发布系统级 + 工作空间级 + 剧本级）
     * 跨 schema 查询，使用 @ResultMap 确保 JSONB 字段正确反序列化
     */
    @ResultMap("mybatis-plus_Style")
    @Select("""
            SELECT * FROM (
                SELECT * FROM tenant_system.t_style WHERE scope = 'SYSTEM' AND deleted = 0
                UNION ALL
                SELECT * FROM t_style WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                UNION ALL
                SELECT * FROM t_style WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
            ) combined ORDER BY scope, updated_at DESC
            """)
    List<Style> selectAvailableStyles(@Param("workspaceId") String workspaceId, @Param("scriptId") String scriptId);

    /**
     * 查询剧本可用的所有风格（带数据库级关键字过滤和数量限制）
     * keyword 条件下推到每个 UNION 分支内，以便利用各自表的索引
     */
    @ResultMap("mybatis-plus_Style")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_style WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_style WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_style WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            ORDER BY scope, updated_at DESC
            <if test="limit != null and limit > 0">
                LIMIT #{limit}
            </if>
            </script>
            """)
    List<Style> selectAvailableStylesFiltered(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);

    /**
     * 跨 schema 分页查询风格（SYSTEM + WORKSPACE + SCRIPT）
     * keyword 条件下推到每个 UNION 分支内，以便利用各自表的索引
     */
    @ResultMap("mybatis-plus_Style")
    @Select("""
            <script>
            SELECT * FROM (
                SELECT * FROM tenant_system.t_style WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_style WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT * FROM t_style WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            ORDER BY scope, updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Style> selectAvailableStylesPaginated(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 跨 schema 统计风格总数（SYSTEM + WORKSPACE + SCRIPT）
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT id FROM tenant_system.t_style WHERE scope = 'SYSTEM' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT id FROM t_style WHERE workspace_id = #{workspaceId} AND scope = 'WORKSPACE' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
                UNION ALL
                SELECT id FROM t_style WHERE script_id = #{scriptId} AND scope = 'SCRIPT' AND deleted = 0
                <if test="keyword != null and keyword != ''">
                    AND (name ILIKE '%' || #{keyword} || '%'
                        OR description ILIKE '%' || #{keyword} || '%'
                        OR fixed_desc ILIKE '%' || #{keyword} || '%'
                        OR style_params::text ILIKE '%' || #{keyword} || '%'
                        OR extra_info::text ILIKE '%' || #{keyword} || '%')
                </if>
            ) combined
            </script>
            """)
    long countAvailableStyles(
            @Param("workspaceId") String workspaceId,
            @Param("scriptId") String scriptId,
            @Param("keyword") String keyword);

    @ResultMap("mybatis-plus_Style")
    @Select("SELECT * FROM tenant_system.t_style WHERE scope = 'SYSTEM' AND deleted = 0 ORDER BY published_at DESC")
    List<Style> selectSystemStyles();

    @ResultMap("mybatis-plus_Style")
    @Select("SELECT * FROM tenant_system.t_style WHERE deleted = 0 ORDER BY created_at DESC")
    List<Style> selectSystemStyleDrafts();

    @Update("""
            UPDATE tenant_system.t_style
            SET scope = 'SYSTEM', published_at = #{publishedAt}, published_by = #{publishedBy},
                publish_note = #{publishNote}, updated_at = CURRENT_TIMESTAMP, updated_by = #{publishedBy}
            WHERE id = #{id} AND deleted = 0
            """)
    int publishStyle(@Param("id") String id,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("publishedBy") String publishedBy,
                     @Param("publishNote") String publishNote);

    @Update("""
            UPDATE tenant_system.t_style
            SET scope = 'WORKSPACE', published_at = NULL, published_by = NULL, publish_note = NULL,
                updated_at = CURRENT_TIMESTAMP, updated_by = #{operatorId}
            WHERE id = #{id} AND deleted = 0
            """)
    int unpublishStyle(@Param("id") String id, @Param("operatorId") String operatorId);

    @ResultMap("mybatis-plus_Style")
    @Select("SELECT * FROM tenant_system.t_style WHERE id = #{id} AND deleted = 0")
    Style selectSystemStyleById(@Param("id") String id);
}
