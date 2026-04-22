package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.ai.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 提示词模板 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （variables, defaultParams）。
 *
 * @author Actionow
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    /**
     * 查询工作空间可用的模板（包含系统模板和工作空间模板）
     */
    default List<PromptTemplate> selectAvailableTemplates(String workspaceId, String type) {
        return selectList(new LambdaQueryWrapper<PromptTemplate>()
                .and(w -> w.eq(PromptTemplate::getWorkspaceId, workspaceId)
                        .or()
                        .eq(PromptTemplate::getScope, "SYSTEM"))
                .eq(PromptTemplate::getType, type)
                .eq(PromptTemplate::getStatus, "ACTIVE")
                .orderByDesc(PromptTemplate::getUseCount));
    }

    /**
     * 查询系统模板
     */
    default List<PromptTemplate> selectSystemTemplates(String type) {
        return selectList(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScope, "SYSTEM")
                .eq(PromptTemplate::getType, type)
                .eq(PromptTemplate::getStatus, "ACTIVE")
                .orderByDesc(PromptTemplate::getUseCount));
    }

    /**
     * 增加使用次数
     */
    @Update("UPDATE prompt_template SET use_count = use_count + 1 WHERE id = #{id}")
    int incrementUseCount(@Param("id") String id);
}
