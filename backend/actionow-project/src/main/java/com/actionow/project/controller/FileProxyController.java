package com.actionow.project.controller;

import com.actionow.common.file.service.FileStorageService;
import com.actionow.common.security.annotation.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/**
 * 文件代理控制器
 * 通过 Gateway 进行 token 验证后，代理访问 OSS 文件
 * <p>
 * 使用方式：
 * - 前端通过 /api/files/proxy/{bucket}/{path} 访问文件
 * - Gateway 验证 token 后转发到此控制器
 * - 控制器从 OSS 获取文件并返回
 * <p>
 * 优点：
 * - 无需生成预签名 URL
 * - 统一使用公开 URL，由 Gateway 控制访问权限
 * - 支持 CDN 缓存
 * <p>
 * 注意：响应使用 chunked transfer encoding（无 Content-Length）
 * 以避免在代理时缓冲整个文件。对于需要精确进度的场景，
 * 建议使用预签名 URL 直接访问 OSS。
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@RequireLogin
public class FileProxyController {

    private final FileStorageService fileStorageService;

    /**
     * 代理文件访问
     * 路径格式: /files/proxy/{bucket}/{rest-of-path}
     * <p>
     * Gateway 路由配置会将 /api/files/proxy/** 转发到此控制器
     */
    @GetMapping("/proxy/**")
    public ResponseEntity<Resource> proxyFile(HttpServletRequest request) {
        // 提取文件路径：/files/proxy/{bucket}/{path} -> {bucket}/{path}
        String requestUri = request.getRequestURI();
        String filePath = extractFilePath(requestUri);

        if (!StringUtils.hasText(filePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        log.debug("File proxy request: path={}", filePath);

        try {
            // 检查文件是否存在
            if (!fileStorageService.exists(filePath)) {
                log.warn("File not found: {}", filePath);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }

            // 从 OSS 获取文件流
            InputStream inputStream = fileStorageService.download(filePath);
            InputStreamResource resource = new InputStreamResource(inputStream);

            // 推断 Content-Type
            String contentType = inferContentType(filePath);

            // 构建响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            // 允许浏览器缓存
            headers.setCacheControl("public, max-age=31536000");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to proxy file: path={}, error={}", filePath, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve file");
        }
    }

    /**
     * 从请求 URI 中提取文件路径
     * /files/proxy/bucket/path/to/file.jpg -> bucket/path/to/file.jpg
     */
    private String extractFilePath(String requestUri) {
        String prefix = "/files/proxy/";
        int prefixIndex = requestUri.indexOf(prefix);
        if (prefixIndex == -1) {
            return null;
        }
        String path = requestUri.substring(prefixIndex + prefix.length());
        // URL 解码（处理空格等特殊字符）
        try {
            path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode path: {}", path);
        }
        return path;
    }

    /**
     * 根据文件扩展名推断 Content-Type
     */
    private String inferContentType(String filePath) {
        String lowerPath = filePath.toLowerCase();

        // 图片类型
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerPath.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerPath.endsWith(".svg")) {
            return "image/svg+xml";
        }

        // 视频类型
        if (lowerPath.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lowerPath.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowerPath.endsWith(".mov")) {
            return "video/quicktime";
        }

        // 音频类型
        if (lowerPath.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerPath.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerPath.endsWith(".ogg")) {
            return "audio/ogg";
        }

        // 文档类型
        if (lowerPath.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerPath.endsWith(".json")) {
            return "application/json";
        }

        // 默认类型
        return "application/octet-stream";
    }
}
