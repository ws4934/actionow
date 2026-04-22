package com.actionow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.system.entity.DictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据字典类型 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictType> {

    /**
     * 按类型编码查询
     */
    @Select("SELECT * FROM t_dict_type WHERE type_code = #{typeCode} AND deleted = 0")
    DictType selectByCode(@Param("typeCode") String typeCode);

    /**
     * 查询所有启用的字典类型
     */
    @Select("SELECT * FROM t_dict_type WHERE enabled = true AND deleted = 0 ORDER BY sort_order")
    List<DictType> selectAllEnabled();
}
