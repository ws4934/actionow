package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.InspirationRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 灵感生成记录 Mapper。
 *
 * <p><b>已 deprecated</b>：随 {@link InspirationRecord} 一同冻结。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Mapper
public interface InspirationRecordMapper extends BaseMapper<InspirationRecord> {

    /**
     * 根据 sessionId 查询记录列表（按创建时间升序）
     */
    default List<InspirationRecord> selectBySessionId(String sessionId) {
        return selectList(new LambdaQueryWrapper<InspirationRecord>()
                .eq(InspirationRecord::getSessionId, sessionId)
                .eq(InspirationRecord::getDeleted, 0)
                .orderByAsc(InspirationRecord::getCreatedAt));
    }

    /**
     * 根据 taskId 查询记录
     */
    default InspirationRecord selectByTaskId(String taskId) {
        return selectOne(new LambdaQueryWrapper<InspirationRecord>()
                .eq(InspirationRecord::getTaskId, taskId)
                .eq(InspirationRecord::getDeleted, 0));
    }
}
