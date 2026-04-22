package com.actionow.common.file.exception;

import com.actionow.common.core.result.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件服务错误码枚举
 * 格式: 50xxx（文件服务错误码段）
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum FileErrorCode implements IResultCode {

    // ==================== 上传相关 (501xx) ====================
    UPLOAD_FAILED("50101", "文件上传失败"),
    UPLOAD_NOT_COMPLETED("50102", "文件上传未完成"),
    MULTIPART_UPLOAD_ID_INVALID("50103", "无效的分片上传ID"),
    DOWNLOAD_EXTERNAL_FILE_FAILED("50104", "下载外部文件失败"),
    PRESIGNED_URL_FAILED("50105", "生成预签名URL失败"),

    // ==================== 文件相关 (502xx) ====================
    FILE_TYPE_NOT_ALLOWED("50201", "不支持的文件类型"),
    FILE_SIZE_EXCEEDED("50202", "文件大小超出限制"),
    FILE_NAME_INVALID("50203", "文件名无效"),
    FILE_NOT_FOUND("50204", "文件不存在"),
    FILE_DELETE_FAILED("50205", "文件删除失败"),

    // ==================== 缩略图相关 (503xx) ====================
    THUMBNAIL_GENERATION_FAILED("50301", "缩略图生成失败"),
    THUMBNAIL_TYPE_NOT_SUPPORTED("50302", "不支持生成该类型的缩略图");

    private final String code;
    private final String message;
}
