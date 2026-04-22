package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.ai.entity.ModelProviderSchema;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 模型提供商 I/O Schema Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段。
 * 注意: @Select 原生 SQL 会绕过 autoResultMap，导致 JSONB 列返回 null。
 *
 * @author Actionow
 */
@Mapper
public interface ModelProviderSchemaMapper extends BaseMapper<ModelProviderSchema> {

    default ModelProviderSchema selectByProviderId(String providerId) {
        return selectOne(new LambdaQueryWrapper<ModelProviderSchema>()
                .eq(ModelProviderSchema::getProviderId, providerId)
                .eq(ModelProviderSchema::getDeleted, 0)
                .last("LIMIT 1"));
    }

    default List<ModelProviderSchema> selectByProviderIds(List<String> providerIds) {
        return selectList(new LambdaQueryWrapper<ModelProviderSchema>()
                .in(ModelProviderSchema::getProviderId, providerIds)
                .eq(ModelProviderSchema::getDeleted, 0));
    }
}
