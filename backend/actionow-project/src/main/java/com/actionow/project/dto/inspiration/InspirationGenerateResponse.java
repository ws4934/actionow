package com.actionow.project.dto.inspiration;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 灵感生成响应
 *
 * @author Actionow
 */
@Data
public class InspirationGenerateResponse {

    private String recordId;
    private List<String> assetIds;
    private String taskId;
    private String taskStatus;
    private BigDecimal creditCost;
    private Boolean success;
    private String errorMessage;
}
