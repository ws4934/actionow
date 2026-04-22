package com.actionow.common.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.data.entity.BaseEntity;

import java.util.List;

/**
 * 基础 Service 实现类
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 * @author Actionow
 */
public abstract class BaseServiceImpl<M extends BaseMapper<T>, T extends BaseEntity> extends ServiceImpl<M, T> {

    /**
     * 分页查询
     */
    public PageResult<T> page(Long current, Long size, LambdaQueryWrapper<T> queryWrapper) {
        Page<T> page = new Page<>(current, size);
        Page<T> result = this.page(page, queryWrapper);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 分页查询（默认包装器）
     */
    public PageResult<T> page(Long current, Long size) {
        return page(current, size, new LambdaQueryWrapper<>());
    }

    /**
     * 根据ID查询，返回 Optional
     */
    public java.util.Optional<T> findById(String id) {
        return java.util.Optional.ofNullable(getById(id));
    }

    /**
     * 根据ID列表查询
     */
    public List<T> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return listByIds(ids);
    }

    /**
     * 判断是否存在
     */
    public boolean existsById(String id) {
        return getById(id) != null;
    }

    /**
     * 保存或更新，返回实体
     */
    public T saveOrUpdateAndReturn(T entity) {
        saveOrUpdate(entity);
        return entity;
    }
}
