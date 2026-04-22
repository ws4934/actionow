package com.actionow.agent.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.tool.entity.AgentToolAccess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent 工具访问权限 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AgentToolAccessMapper extends BaseMapper<AgentToolAccess> {

    /**
     * 根据 Agent 类型查询启用的工具权限
     */
    @Select("SELECT * FROM t_agent_tool_access WHERE agent_type = #{agentType} " +
            "AND enabled = true AND deleted = 0 ORDER BY tool_category, priority DESC")
    List<AgentToolAccess> selectEnabledByAgentType(@Param("agentType") String agentType);

    /**
     * 根据 Agent 类型和工具分类查询
     */
    @Select("SELECT * FROM t_agent_tool_access WHERE agent_type = #{agentType} " +
            "AND tool_category = #{toolCategory} AND enabled = true AND deleted = 0 " +
            "ORDER BY priority DESC")
    List<AgentToolAccess> selectByAgentTypeAndCategory(@Param("agentType") String agentType,
                                                        @Param("toolCategory") String toolCategory);

    /**
     * 根据工具 ID 查询（哪些 Agent 可使用）
     */
    @Select("SELECT * FROM t_agent_tool_access WHERE tool_id = #{toolId} " +
            "AND enabled = true AND deleted = 0 ORDER BY agent_type")
    List<AgentToolAccess> selectByToolId(@Param("toolId") String toolId);

    /**
     * 查询 Agent 是否有权限访问工具
     */
    @Select("SELECT * FROM t_agent_tool_access WHERE agent_type = #{agentType} " +
            "AND tool_category = #{toolCategory} AND tool_id = #{toolId} " +
            "AND enabled = true AND deleted = 0 LIMIT 1")
    AgentToolAccess selectByAgentAndTool(@Param("agentType") String agentType,
                                          @Param("toolCategory") String toolCategory,
                                          @Param("toolId") String toolId);

    /**
     * 查询所有启用的工具权限
     */
    @Select("SELECT * FROM t_agent_tool_access WHERE enabled = true AND deleted = 0 " +
            "ORDER BY agent_type, tool_category, priority DESC")
    List<AgentToolAccess> selectAllEnabled();

    /**
     * 分页查询
     */
    @Select("<script>" +
            "SELECT * FROM t_agent_tool_access WHERE deleted = 0 " +
            "<if test='agentType != null'> AND agent_type = #{agentType} </if>" +
            "<if test='toolCategory != null'> AND tool_category = #{toolCategory} </if>" +
            "<if test='toolId != null'> AND tool_id = #{toolId} </if>" +
            "<if test='enabled != null'> AND enabled = #{enabled} </if>" +
            "ORDER BY agent_type, tool_category, priority DESC" +
            "</script>")
    IPage<AgentToolAccess> selectPage(Page<AgentToolAccess> page,
                                       @Param("agentType") String agentType,
                                       @Param("toolCategory") String toolCategory,
                                       @Param("toolId") String toolId,
                                       @Param("enabled") Boolean enabled);
}
