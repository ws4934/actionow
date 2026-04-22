package com.actionow.agent.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 消息附件信息（前端渲染用）
 */
@Data
@Builder
public class AttachmentInfo {

    private String assetId;
    private String url;
    private String thumbnailUrl;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    /** IMAGE / VIDEO / AUDIO / DOCUMENT / OTHER */
    private String assetType;
}
