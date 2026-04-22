package com.actionow.project.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件服务 Feign 客户端
 * 调用 actionow-asset 的文件服务内部接口
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-asset", path = "/internal/files", fallbackFactory = FileFeignClientFallbackFactory.class)
public interface FileFeignClient {

    /**
     * 从 URL 转存文件到本地 OSS
     *
     * @param request 转存请求
     * @return 上传结果
     */
    @PostMapping("/transfer")
    Result<FileUploadResult> transferFromUrl(@RequestBody FileTransferRequest request);

    /**
     * 删除文件
     *
     * @param fileKey 文件 Key
     * @return 操作结果
     */
    @DeleteMapping
    Result<Void> deleteFile(@RequestParam("fileKey") String fileKey);

    /**
     * 批量删除文件
     *
     * @param fileKeys 文件 Key 列表
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    Result<Void> deleteFiles(@RequestBody List<String> fileKeys);

    /**
     * 获取文件访问 URL
     *
     * @param fileKey 文件 Key
     * @return 文件 URL
     */
    @GetMapping("/url")
    Result<String> getFileUrl(@RequestParam("fileKey") String fileKey);

    /**
     * 检查文件是否存在
     *
     * @param fileKey 文件 Key
     * @return 是否存在
     */
    @GetMapping("/exists")
    Result<Boolean> exists(@RequestParam("fileKey") String fileKey);

    /**
     * 生成缩略图
     *
     * @param fileKey  文件 Key
     * @param mimeType MIME 类型
     * @return 缩略图 URL
     */
    @PostMapping("/thumbnail")
    Result<String> generateThumbnail(@RequestParam("fileKey") String fileKey,
                                     @RequestParam("mimeType") String mimeType);

    /**
     * 获取预签名下载 URL
     *
     * @param fileKey       文件 Key
     * @param expireSeconds 有效期（秒）
     * @return 下载 URL
     */
    @GetMapping("/download-url")
    Result<String> getDownloadUrl(@RequestParam("fileKey") String fileKey,
                                  @RequestParam(value = "expireSeconds", defaultValue = "3600") int expireSeconds);
}
