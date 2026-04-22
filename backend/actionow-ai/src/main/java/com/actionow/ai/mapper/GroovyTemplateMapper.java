package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.ai.entity.GroovyTemplate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Groovy模板Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （exampleInput, exampleOutput）。
 *
 * @author Actionow
 */
@Mapper
public interface GroovyTemplateMapper extends BaseMapper<GroovyTemplate> {

    /**
     * 查询系统模板
     */
    default List<GroovyTemplate> selectSystemTemplates() {
        return selectList(new LambdaQueryWrapper<GroovyTemplate>()
                .eq(GroovyTemplate::getIsSystem, true)
                .eq(GroovyTemplate::getEnabled, true)
                .orderByAsc(GroovyTemplate::getTemplateType)
                .orderByAsc(GroovyTemplate::getName));
    }

    /**
     * 按类型查询模板
     */
    default List<GroovyTemplate> selectByType(String templateType) {
        return selectList(new LambdaQueryWrapper<GroovyTemplate>()
                .eq(GroovyTemplate::getTemplateType, templateType)
                .eq(GroovyTemplate::getEnabled, true)
                .orderByDesc(GroovyTemplate::getIsSystem)
                .orderByAsc(GroovyTemplate::getName));
    }

    /**
     * 按生成类型查询模板
     */
    default List<GroovyTemplate> selectByGenerationType(String generationType) {
        return selectList(new LambdaQueryWrapper<GroovyTemplate>()
                .and(w -> w.eq(GroovyTemplate::getGenerationType, generationType)
                        .or()
                        .eq(GroovyTemplate::getGenerationType, "ALL"))
                .eq(GroovyTemplate::getEnabled, true)
                .orderByDesc(GroovyTemplate::getIsSystem)
                .orderByAsc(GroovyTemplate::getName));
    }

    /**
     * 按名称查询
     */
    default GroovyTemplate selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<GroovyTemplate>()
                .eq(GroovyTemplate::getName, name)
                .last("LIMIT 1"));
    }

    /**
     * 分页查询模板
     */
    default IPage<GroovyTemplate> selectPage(Page<GroovyTemplate> page,
                                              String templateType, String generationType,
                                              Boolean isSystem, String name) {
        LambdaQueryWrapper<GroovyTemplate> wrapper = new LambdaQueryWrapper<GroovyTemplate>()
                .eq(templateType != null && !templateType.isEmpty(),
                        GroovyTemplate::getTemplateType, templateType)
                .and(generationType != null && !generationType.isEmpty(),
                        w -> w.eq(GroovyTemplate::getGenerationType, generationType)
                                .or()
                                .eq(GroovyTemplate::getGenerationType, "ALL"))
                .eq(isSystem != null, GroovyTemplate::getIsSystem, isSystem)
                .like(name != null && !name.isEmpty(), GroovyTemplate::getName, name)
                .orderByDesc(GroovyTemplate::getIsSystem)
                .orderByAsc(GroovyTemplate::getName);
        return selectPage(page, wrapper);
    }
}
