package com.actionow.project.dto.version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 恢复版本请求 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreVersionRequest {

    /**
     * 要恢复到的版本号
     */
    @NotNull(message = "版本号不能为空")
    private Integer versionNumber;

    /**
     * 恢复原因/说明
     */
    private String reason;
}
