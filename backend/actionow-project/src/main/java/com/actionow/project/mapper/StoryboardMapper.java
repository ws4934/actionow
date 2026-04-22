package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Storyboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

/**
 * 分镜 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （visualDesc, audioDesc, extraInfo）。
 *
 * @author Actionow
 */
@Mapper
public interface StoryboardMapper extends BaseMapper<Storyboard> {

    /**
     * 根据剧集ID查询分镜列表（按序号排序）
     */
    default List<Storyboard> selectByEpisodeId(String episodeId) {
        return selectList(new LambdaQueryWrapper<Storyboard>()
                .eq(Storyboard::getEpisodeId, episodeId)
                .orderByAsc(Storyboard::getSequence));
    }

    /**
     * 根据剧本ID查询所有分镜
     */
    default List<Storyboard> selectByScriptId(String scriptId) {
        return selectList(new LambdaQueryWrapper<Storyboard>()
                .eq(Storyboard::getScriptId, scriptId)
                .orderByAsc(Storyboard::getEpisodeId)
                .orderByAsc(Storyboard::getSequence));
    }

    /**
     * 获取剧集的最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM t_storyboard WHERE episode_id = #{episodeId} AND deleted = 0")
    int getMaxSequence(@Param("episodeId") String episodeId);

    /**
     * 统计剧集的分镜数量
     */
    @Select("SELECT COUNT(*) FROM t_storyboard WHERE episode_id = #{episodeId} AND deleted = 0")
    int countByEpisodeId(@Param("episodeId") String episodeId);

    /**
     * 批量统计多个剧集的分镜数量，避免 N+1 查询
     */
    @MapKey("episodeId")
    @Select("<script>" +
            "SELECT episode_id as episodeId, COUNT(*) as count " +
            "FROM t_storyboard " +
            "WHERE deleted = 0 AND episode_id IN " +
            "<foreach collection='episodeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "GROUP BY episode_id" +
            "</script>")
    Map<String, Map<String, Object>> batchCountByEpisodeIdsRaw(@Param("episodeIds") List<String> episodeIds);

    /**
     * 批量统计多个剧集的分镜数量
     */
    default Map<String, Integer> batchCountByEpisodeIds(List<String> episodeIds) {
        if (episodeIds == null || episodeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> rawResult = batchCountByEpisodeIdsRaw(episodeIds);
        return rawResult.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ((Number) e.getValue().get("count")).intValue()
                ));
    }
}
