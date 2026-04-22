package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class FileFeignClientFallbackFactory implements FallbackFactory<FileFeignClient> {

    @Override
    public FileFeignClient create(Throwable cause) {
        log.error("调用文件服务失败: {}", cause.getMessage());
        return new FileFeignClient() {
            @Override
            public Result<FileUploadResult> transferFromUrl(FileTransferRequest request) {
                log.warn("文件转存降级: sourceUrl={}", request.getSourceUrl());
                return Result.fail("文件服务暂不可用");
            }

            @Override
            public Result<Void> deleteFile(String fileKey) {
                log.warn("删除文件降级: fileKey={}", fileKey);
                return Result.fail("文件服务暂不可用");
            }

            @Override
            public Result<Void> deleteFiles(List<String> fileKeys) {
                log.warn("批量删除文件降级: fileKeys={}", fileKeys);
                return Result.fail("文件服务暂不可用");
            }

            @Override
            public Result<String> getFileUrl(String fileKey) {
                log.warn("获取文件URL降级: fileKey={}", fileKey);
                return Result.success(null);
            }

            @Override
            public Result<Boolean> exists(String fileKey) {
                log.warn("检查文件存在降级: fileKey={}", fileKey);
                return Result.success(false);
            }

            @Override
            public Result<String> generateThumbnail(String fileKey, String mimeType) {
                log.warn("生成缩略图降级: fileKey={}", fileKey);
                return Result.success(null);
            }

            @Override
            public Result<String> getDownloadUrl(String fileKey, int expireSeconds) {
                log.warn("获取下载URL降级: fileKey={}", fileKey);
                return Result.success(null);
            }
        };
    }
}
