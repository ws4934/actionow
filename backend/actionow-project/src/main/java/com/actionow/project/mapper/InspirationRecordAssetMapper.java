package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.InspirationRecordAsset;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;

/**
 * 灵感记录资产关联 Mapper。
 *
 * <p><b>已 deprecated</b>：随 {@link InspirationRecordAsset} 一同冻结。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Mapper
public interface InspirationRecordAssetMapper extends BaseMapper<InspirationRecordAsset> {

    /**
     * 根据 recordId 查询资产列表
     */
    default List<InspirationRecordAsset> selectByRecordId(String recordId) {
        return selectList(new LambdaQueryWrapper<InspirationRecordAsset>()
                .eq(InspirationRecordAsset::getRecordId, recordId)
                .eq(InspirationRecordAsset::getDeleted, 0)
                .orderByAsc(InspirationRecordAsset::getCreatedAt));
    }

    /**
     * 根据多个 recordId 批量查询资产
     */
    default List<InspirationRecordAsset> selectByRecordIds(Set<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<InspirationRecordAsset>()
                .in(InspirationRecordAsset::getRecordId, recordIds)
                .eq(InspirationRecordAsset::getDeleted, 0)
                .orderByAsc(InspirationRecordAsset::getCreatedAt));
    }
}
