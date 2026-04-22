package com.actionow.ai.dto;

import com.actionow.ai.dto.schema.ExclusiveGroup;
import com.actionow.ai.dto.schema.InputParamDefinition;
import com.actionow.ai.dto.schema.InputParamGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新 Schema 请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchemaRequest {

    /**
     * 输入参数定义列表
     */
    private List<InputParamDefinition> params;

    /**
     * 参数分组列表
     */
    private List<InputParamGroup> groups;

    /**
     * 互斥参数组列表
     */
    private List<ExclusiveGroup> exclusiveGroups;
}
