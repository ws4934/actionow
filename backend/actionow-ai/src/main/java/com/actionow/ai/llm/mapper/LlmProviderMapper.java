package com.actionow.ai.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.ai.llm.entity.LlmProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LLM Provider Mapper
 *
 * @author Actionow
 */
@Mapper
public interface LlmProviderMapper extends BaseMapper<LlmProvider> {

    /**
     * 根据厂商查询启用的 Provider
     */
    @Select("SELECT * FROM t_llm_provider WHERE provider = #{provider} AND enabled = true AND deleted = 0 ORDER BY priority DESC")
    List<LlmProvider> selectEnabledByProvider(@Param("provider") String provider);

    /**
     * 根据厂商和模型ID查询
     */
    @Select("SELECT * FROM t_llm_provider WHERE provider = #{provider} AND model_id = #{modelId} AND deleted = 0")
    LlmProvider selectByProviderAndModelId(@Param("provider") String provider, @Param("modelId") String modelId);

    /**
     * 查询所有启用的 Provider
     */
    @Select("SELECT * FROM t_llm_provider WHERE enabled = true AND deleted = 0 ORDER BY priority DESC")
    List<LlmProvider> selectAllEnabled();

    /**
     * 分页查询
     */
    @Select("<script>" +
            "SELECT * FROM t_llm_provider WHERE deleted = 0 " +
            "<if test='provider != null'> AND provider = #{provider} </if>" +
            "<if test='enabled != null'> AND enabled = #{enabled} </if>" +
            "<if test='modelName != null and modelName != \"\"'> AND model_name LIKE CONCAT('%', #{modelName}, '%') </if>" +
            "ORDER BY priority DESC, created_at DESC" +
            "</script>")
    IPage<LlmProvider> selectPage(Page<LlmProvider> page,
                                  @Param("provider") String provider,
                                  @Param("enabled") Boolean enabled,
                                  @Param("modelName") String modelName);
}
