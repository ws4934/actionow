package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.ai.entity.ModelProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型提供商Mapper
 *
 * @author Actionow
 */
@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProvider> {

    /**
     * 根据插件ID查询提供商
     */
    @Select("SELECT * FROM t_model_provider WHERE plugin_id = #{pluginId} AND deleted = 0")
    List<ModelProvider> selectByPluginId(@Param("pluginId") String pluginId);

    /**
     * 根据提供商类型查询启用的提供商
     */
    @Select("SELECT * FROM t_model_provider WHERE provider_type = #{providerType} AND enabled = true AND deleted = 0 ORDER BY priority DESC")
    List<ModelProvider> selectEnabledByType(@Param("providerType") String providerType);

    /**
     * 查询所有启用的提供商
     */
    @Select("SELECT * FROM t_model_provider WHERE enabled = true AND deleted = 0 ORDER BY priority DESC")
    List<ModelProvider> selectAllEnabled();

    /**
     * 分页查询所有提供商（包含禁用的）
     */
    @Select("<script>" +
            "SELECT * FROM t_model_provider WHERE deleted = 0 " +
            "<if test='providerType != null'> AND provider_type = #{providerType} </if>" +
            "<if test='enabled != null'> AND enabled = #{enabled} </if>" +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%') </if>" +
            "ORDER BY priority DESC, created_at DESC" +
            "</script>")
    IPage<ModelProvider> selectPage(Page<ModelProvider> page,
                                    @Param("providerType") String providerType,
                                    @Param("enabled") Boolean enabled,
                                    @Param("name") String name);
}
