package com.actionow.project.dto.library;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布/下架资源请求
 *
 * @author Actionow
 */
@Data
public class PublishResourceRequest {

    /**
     * 发布说明（可选，不超过 500 字）
     */
    @Size(max = 500, message = "发布说明不超过 500 字")
    private String publishNote;
}
