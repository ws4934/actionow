package com.actionow.system.service;

import com.actionow.system.dto.DictItemResponse;
import com.actionow.system.dto.DictTypeResponse;

import java.util.List;

/**
 * 数据字典服务接口
 *
 * @author Actionow
 */
public interface DictService {

    /**
     * 获取所有字典类型
     */
    List<DictTypeResponse> listAllTypes();

    /**
     * 获取字典类型详情（含字典项）
     */
    DictTypeResponse getTypeByCode(String typeCode);

    /**
     * 获取字典项列表
     */
    List<DictItemResponse> listItemsByTypeCode(String typeCode);

    /**
     * 获取字典项
     */
    DictItemResponse getItem(String typeCode, String itemCode);

    /**
     * 获取默认字典项
     */
    DictItemResponse getDefaultItem(String typeCode);

    /**
     * 刷新字典缓存
     */
    void refreshCache();
}
