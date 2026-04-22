package com.actionow.common.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认上传请求
 * 用于验证预签名上传是否成功
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadRequest {

    /**
     * 文件存储 Key
     */
    @NotBlank(message = "fileKey不能为空")
    private String fileKey;
}
