package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.ai.entity.ModelProviderScript;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 模型提供商脚本 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（pricingRules）。
 * 注意: @Select 原生 SQL 会绕过 autoResultMap，导致 JSONB 列返回 null。
 *
 * @author Actionow
 */
@Mapper
public interface ModelProviderScriptMapper extends BaseMapper<ModelProviderScript> {

    default ModelProviderScript selectByProviderId(String providerId) {
        return selectOne(new LambdaQueryWrapper<ModelProviderScript>()
                .eq(ModelProviderScript::getProviderId, providerId)
                .eq(ModelProviderScript::getDeleted, 0)
                .last("LIMIT 1"));
    }

    default List<ModelProviderScript> selectByProviderIds(List<String> providerIds) {
        return selectList(new LambdaQueryWrapper<ModelProviderScript>()
                .in(ModelProviderScript::getProviderId, providerIds)
                .eq(ModelProviderScript::getDeleted, 0));
    }
}
