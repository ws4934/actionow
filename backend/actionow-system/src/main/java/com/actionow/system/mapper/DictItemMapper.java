package com.actionow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.system.entity.DictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据字典项 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {

    /**
     * 按字典类型编码查询字典项
     */
    @Select("SELECT * FROM t_dict_item WHERE type_code = #{typeCode} AND enabled = true " +
            "AND deleted = 0 ORDER BY sort_order")
    List<DictItem> selectByTypeCode(@Param("typeCode") String typeCode);

    /**
     * 按字典类型ID查询字典项
     */
    @Select("SELECT * FROM t_dict_item WHERE type_id = #{typeId} AND deleted = 0 ORDER BY sort_order")
    List<DictItem> selectByTypeId(@Param("typeId") String typeId);

    /**
     * 按字典项编码查询
     */
    @Select("SELECT * FROM t_dict_item WHERE type_code = #{typeCode} AND item_code = #{itemCode} " +
            "AND deleted = 0")
    DictItem selectByItemCode(@Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

    /**
     * 查询默认字典项
     */
    @Select("SELECT * FROM t_dict_item WHERE type_code = #{typeCode} AND is_default = true " +
            "AND deleted = 0 LIMIT 1")
    DictItem selectDefaultItem(@Param("typeCode") String typeCode);
}
