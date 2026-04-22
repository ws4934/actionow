package com.actionow.system.service.impl;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.system.constant.SystemConstants;
import com.actionow.system.dto.DictItemResponse;
import com.actionow.system.dto.DictTypeResponse;
import com.actionow.system.entity.DictItem;
import com.actionow.system.entity.DictType;
import com.actionow.system.mapper.DictItemMapper;
import com.actionow.system.mapper.DictTypeMapper;
import com.actionow.system.service.DictService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据字典服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_HOURS = 24;

    @Override
    public List<DictTypeResponse> listAllTypes() {
        return dictTypeMapper.selectAllEnabled().stream()
                .map(this::toTypeResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DictTypeResponse getTypeByCode(String typeCode) {
        // 尝试从缓存获取
        String cacheKey = SystemConstants.CacheKey.DICT_PREFIX + typeCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DictTypeResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("解析字典缓存失败: {}", e.getMessage());
            }
        }

        DictType dictType = dictTypeMapper.selectByCode(typeCode);
        if (dictType == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典类型不存在");
        }

        DictTypeResponse response = toTypeResponse(dictType);
        response.setItems(listItemsByTypeCode(typeCode));

        // 更新缓存
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                    CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("缓存字典失败: {}", e.getMessage());
        }

        return response;
    }

    @Override
    public List<DictItemResponse> listItemsByTypeCode(String typeCode) {
        return dictItemMapper.selectByTypeCode(typeCode).stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DictItemResponse getItem(String typeCode, String itemCode) {
        DictItem item = dictItemMapper.selectByItemCode(typeCode, itemCode);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        return toItemResponse(item);
    }

    @Override
    public DictItemResponse getDefaultItem(String typeCode) {
        DictItem item = dictItemMapper.selectDefaultItem(typeCode);
        if (item == null) {
            // 返回第一个作为默认
            List<DictItem> items = dictItemMapper.selectByTypeCode(typeCode);
            if (items.isEmpty()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "字典项不存在");
            }
            item = items.get(0);
        }
        return toItemResponse(item);
    }

    @Override
    public void refreshCache() {
        List<DictType> types = dictTypeMapper.selectAllEnabled();
        for (DictType type : types) {
            String cacheKey = SystemConstants.CacheKey.DICT_PREFIX + type.getTypeCode();
            redisTemplate.delete(cacheKey);
        }
        log.info("刷新字典缓存: count={}", types.size());
    }

    private DictTypeResponse toTypeResponse(DictType dictType) {
        DictTypeResponse response = new DictTypeResponse();
        BeanUtils.copyProperties(dictType, response);
        return response;
    }

    private DictItemResponse toItemResponse(DictItem item) {
        DictItemResponse response = new DictItemResponse();
        BeanUtils.copyProperties(item, response);
        return response;
    }
}
