package com.actionow.task.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.task.entity.Pipeline;
import org.apache.ibatis.annotations.Mapper;

/**
 * Pipeline Mapper
 *
 * @author Actionow
 */
@Mapper
public interface PipelineMapper extends BaseMapper<Pipeline> {

    /**
     * 根据批量作业ID查询 Pipeline
     */
    default Pipeline selectByBatchJobId(String batchJobId) {
        return selectOne(new LambdaQueryWrapper<Pipeline>()
                .eq(Pipeline::getBatchJobId, batchJobId)
                .last("LIMIT 1"));
    }
}
