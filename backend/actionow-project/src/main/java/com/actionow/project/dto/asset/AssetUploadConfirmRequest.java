package com.actionow.project.dto.asset;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 素材上传确认请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadConfirmRequest {

    /**
     * 文件 Key（用于验证）
     */
    @NotBlank(message = "fileKey不能为空")
    private String fileKey;

    /**
     * 实际文件大小（可选，用于校验）
     */
    private Long actualFileSize;

    /**
     * 文件元数据（宽度、高度、时长等，可选）
     */
    private Map<String, Object> metaInfo;
}
