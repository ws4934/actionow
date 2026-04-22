package com.actionow.agent.dto.request;

import lombok.Data;

/**
 * 内联附件 DTO
 * 支持 base64 编码或 URL 引用的多模态数据（图片、视频、音频、文档）
 * <p>
 * data 和 url 二选一：
 * - data 模式：前端传 base64 编码数据，mimeType 必填
 * - url 模式：前端传媒体 URL，后端自动下载，mimeType 可选（自动推断）
 *
 * @author Actionow
 */
@Data
public class InlineAttachment {

    /**
     * MIME 类型，例如 "image/jpeg", "application/pdf"
     * base64 模式必填；URL 模式可选（从扩展名或 Content-Type 推断）
     */
    private String mimeType;

    /**
     * base64 编码数据（不含 data URI 前缀），与 url 互斥
     */
    private String data;

    /**
     * 媒体 URL（与 data 互斥），后端自动下载内容传递给 Gemini
     */
    private String url;

    /**
     * 文件名（可选，用于展示）
     */
    private String fileName;

    /**
     * 文件大小，字节数（可选）
     */
    private Long fileSize;
}
