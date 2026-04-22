package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Episode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

/**
 * 剧集 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface EpisodeMapper extends BaseMapper<Episode> {

    /**
     * 根据剧本ID查询剧集列表（按序号排序）
     */
    @Select("SELECT * FROM t_episode WHERE script_id = #{scriptId} AND deleted = 0 ORDER BY sequence ASC")
    List<Episode> selectByScriptId(@Param("scriptId") String scriptId);

    /**
     * 获取剧本的最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM t_episode WHERE script_id = #{scriptId} AND deleted = 0")
    int getMaxSequence(@Param("scriptId") String scriptId);

    /**
     * 统计剧本的剧集数量
     */
    @Select("SELECT COUNT(*) FROM t_episode WHERE script_id = #{scriptId} AND deleted = 0")
    int countByScriptId(@Param("scriptId") String scriptId);

    /**
     * 批量统计多个剧本的剧集数量，避免 N+1 查询
     */
    @MapKey("scriptId")
    @Select("<script>" +
            "SELECT script_id as scriptId, COUNT(*) as count " +
            "FROM t_episode " +
            "WHERE deleted = 0 AND script_id IN " +
            "<foreach collection='scriptIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "GROUP BY script_id" +
            "</script>")
    Map<String, Map<String, Object>> batchCountByScriptIdsRaw(@Param("scriptIds") List<String> scriptIds);

    /**
     * 批量统计多个剧本的剧集数量
     */
    default Map<String, Integer> batchCountByScriptIds(List<String> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> rawResult = batchCountByScriptIdsRaw(scriptIds);
        return rawResult.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ((Number) e.getValue().get("count")).intValue()
                ));
    }
}
