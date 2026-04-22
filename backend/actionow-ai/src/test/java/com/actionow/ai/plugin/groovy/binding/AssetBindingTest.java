package com.actionow.ai.plugin.groovy.binding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetBinding 单元测试
 */
class AssetBindingTest {

    private AssetBinding assetBinding;

    @BeforeEach
    void setUp() {
        assetBinding = new AssetBinding();
    }

    @Nested
    @DisplayName("isAssetId 测试")
    class IsAssetIdTests {

        @Test
        @DisplayName("应该识别有效的 UUID 格式")
        void shouldRecognizeValidUuid() {
            assertTrue(assetBinding.isAssetId("550e8400-e29b-41d4-a716-446655440000"));
            assertTrue(assetBinding.isAssetId("123e4567-e89b-12d3-a456-426614174000"));
        }

        @Test
        @DisplayName("不应该将 URL 识别为素材 ID")
        void shouldNotRecognizeUrlAsAssetId() {
            assertFalse(assetBinding.isAssetId("https://example.com/image.png"));
            assertFalse(assetBinding.isAssetId("http://example.com/video.mp4"));
        }

        @Test
        @DisplayName("不应该将 Data URI 识别为素材 ID")
        void shouldNotRecognizeDataUriAsAssetId() {
            assertFalse(assetBinding.isAssetId("data:image/png;base64,iVBORw0KGgo="));
        }

        @Test
        @DisplayName("不应该将过长字符串识别为素材 ID")
        void shouldNotRecognizeLongStringAsAssetId() {
            String longString = "a".repeat(101);
            assertFalse(assetBinding.isAssetId(longString));
        }

        @Test
        @DisplayName("null 和空字符串应该返回 false")
        void shouldReturnFalseForNullOrEmpty() {
            assertFalse(assetBinding.isAssetId(null));
            assertFalse(assetBinding.isAssetId(""));
            assertFalse(assetBinding.isAssetId("   "));
        }
    }

    @Nested
    @DisplayName("Base64 编解码测试")
    class Base64Tests {

        @Test
        @DisplayName("应该正确编码为 Base64")
        void shouldEncodeToBase64() {
            byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            String base64 = assetBinding.toBase64(data);
            assertEquals("SGVsbG8sIFdvcmxkIQ==", base64);
        }

        @Test
        @DisplayName("应该正确解码 Base64")
        void shouldDecodeFromBase64() {
            String base64 = "SGVsbG8sIFdvcmxkIQ==";
            byte[] data = assetBinding.fromBase64(base64);
            assertEquals("Hello, World!", new String(data, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("应该能解码包含 Data URI 前缀的 Base64")
        void shouldDecodeBase64WithDataUriPrefix() {
            String dataUri = "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==";
            byte[] data = assetBinding.fromBase64(dataUri);
            assertEquals("Hello, World!", new String(data, StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("文本处理测试")
    class TextProcessingTests {

        @Test
        @DisplayName("应该正确截断文本")
        void shouldTruncateText() {
            String text = "Hello, World!";
            assertEquals("Hello...", assetBinding.truncateText(text, 8, "..."));
        }

        @Test
        @DisplayName("短文本不应该被截断")
        void shouldNotTruncateShortText() {
            String text = "Hello";
            assertEquals("Hello", assetBinding.truncateText(text, 10, "..."));
        }

        @Test
        @DisplayName("null 文本应该返回 null")
        void shouldReturnNullForNullText() {
            assertNull(assetBinding.truncateText(null, 10, "..."));
        }

        @Test
        @DisplayName("应该正确移除 HTML 标签")
        void shouldStripHtmlTags() {
            String html = "<p>Hello <b>World</b>!</p>";
            assertEquals("Hello World!", assetBinding.stripHtml(html));
        }

        @Test
        @DisplayName("应该处理 HTML 实体")
        void shouldHandleHtmlEntities() {
            String html = "Hello&nbsp;&amp;&lt;World&gt;";
            assertEquals("Hello &<World>", assetBinding.stripHtml(html));
        }
    }

    @Nested
    @DisplayName("JSON 解析测试")
    class JsonParsingTests {

        @Test
        @DisplayName("应该正确提取简单 JSON 值")
        void shouldExtractSimpleJsonValue() {
            String json = "{\"name\": \"John\", \"age\": 30}";
            assertEquals("John", assetBinding.extractJsonValue(json, "name"));
            assertEquals("30", assetBinding.extractJsonValue(json, "age"));
        }

        @Test
        @DisplayName("应该支持嵌套路径")
        void shouldSupportNestedPath() {
            String json = "{\"user\": {\"profile\": {\"name\": \"John\"}}}";
            assertEquals("John", assetBinding.extractJsonValue(json, "user.profile.name"));
        }

        @Test
        @DisplayName("应该支持数组索引访问")
        void shouldSupportArrayIndexAccess() {
            String json = "{\"items\": [\"first\", \"second\", \"third\"]}";
            assertEquals("first", assetBinding.extractJsonValue(json, "items.0"));
            assertEquals("second", assetBinding.extractJsonValue(json, "items.1"));
        }

        @Test
        @DisplayName("不存在的路径应该返回 null")
        void shouldReturnNullForNonExistentPath() {
            String json = "{\"name\": \"John\"}";
            assertNull(assetBinding.extractJsonValue(json, "nonexistent"));
            assertNull(assetBinding.extractJsonValue(json, "name.nested"));
        }

        @Test
        @DisplayName("无效 JSON 应该返回 null")
        void shouldReturnNullForInvalidJson() {
            assertNull(assetBinding.extractJsonValue("invalid json", "key"));
            assertNull(assetBinding.extractJsonValue("", "key"));
            assertNull(assetBinding.extractJsonValue(null, "key"));
        }
    }

    @Nested
    @DisplayName("MIME 类型检测测试")
    class MimeTypeDetectionTests {

        @Test
        @DisplayName("应该检测 PNG 格式")
        void shouldDetectPng() {
            // PNG magic bytes: 0x89 0x50 0x4E 0x47
            byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            assertEquals("image/png", assetBinding.detectMimeType(pngBytes));
        }

        @Test
        @DisplayName("应该检测 JPEG 格式")
        void shouldDetectJpeg() {
            // JPEG magic bytes: 0xFF 0xD8 0xFF
            byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            assertEquals("image/jpeg", assetBinding.detectMimeType(jpegBytes));
        }

        @Test
        @DisplayName("应该检测 GIF 格式")
        void shouldDetectGif() {
            // GIF magic bytes: 0x47 0x49 0x46 (GIF)
            byte[] gifBytes = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
            assertEquals("image/gif", assetBinding.detectMimeType(gifBytes));
        }

        @Test
        @DisplayName("应该检测 PDF 格式")
        void shouldDetectPdf() {
            // PDF magic bytes: 0x25 0x50 0x44 0x46 (%PDF)
            byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E};
            assertEquals("application/pdf", assetBinding.detectMimeType(pdfBytes));
        }

        @Test
        @DisplayName("未知格式应该返回 octet-stream")
        void shouldReturnOctetStreamForUnknown() {
            byte[] unknownBytes = {0x00, 0x01, 0x02, 0x03};
            assertEquals("application/octet-stream", assetBinding.detectMimeType(unknownBytes));
        }

        @Test
        @DisplayName("短数据应该返回 octet-stream")
        void shouldReturnOctetStreamForShortData() {
            assertEquals("application/octet-stream", assetBinding.detectMimeType(new byte[]{0x00}));
            assertEquals("application/octet-stream", assetBinding.detectMimeType(null));
        }
    }

    @Nested
    @DisplayName("文件扩展名测试")
    class ExtensionTests {

        @Test
        @DisplayName("应该正确提取扩展名")
        void shouldExtractExtension() {
            assertEquals("png", assetBinding.getExtension("image.png"));
            assertEquals("jpg", assetBinding.getExtension("photo.jpg"));
            assertEquals("mp4", assetBinding.getExtension("video.mp4"));
        }

        @Test
        @DisplayName("扩展名应该转换为小写")
        void shouldConvertExtensionToLowerCase() {
            assertEquals("png", assetBinding.getExtension("image.PNG"));
            assertEquals("jpg", assetBinding.getExtension("photo.JPG"));
        }

        @Test
        @DisplayName("没有扩展名应该返回空字符串")
        void shouldReturnEmptyForNoExtension() {
            assertEquals("", assetBinding.getExtension("filename"));
            assertEquals("", assetBinding.getExtension("filename."));
        }

        @Test
        @DisplayName("null 应该返回 null")
        void shouldReturnNullForNullFilename() {
            assertNull(assetBinding.getExtension(null));
        }
    }

    @Nested
    @DisplayName("文本编码转换测试")
    class EncodingConversionTests {

        @Test
        @DisplayName("应该正确转换 UTF-8 到 GBK")
        void shouldConvertUtf8ToGbk() {
            String text = "中文测试";
            byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);

            // 注意：此测试依赖系统支持 GBK 编码
            try {
                byte[] gbkBytes = assetBinding.convertEncoding(utf8Bytes, "UTF-8", "GBK");
                assertNotNull(gbkBytes);
                assertNotEquals(utf8Bytes.length, gbkBytes.length);
            } catch (IllegalArgumentException e) {
                // 如果系统不支持 GBK，跳过测试
                assertTrue(e.getMessage().contains("不支持的字符集"));
            }
        }

        @Test
        @DisplayName("无效字符集应该抛出异常")
        void shouldThrowExceptionForInvalidCharset() {
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);

            assertThrows(IllegalArgumentException.class, () ->
                assetBinding.convertEncoding(data, "INVALID-CHARSET", "UTF-8")
            );
        }
    }

    @Nested
    @DisplayName("文本摘要提取测试")
    class SummaryExtractionTests {

        @Test
        @DisplayName("应该正确提取指定数量的句子")
        void shouldExtractSpecifiedNumberOfSentences() {
            String text = "第一句。第二句。第三句。第四句。";
            String summary = assetBinding.extractSummary(text, 2);

            // 应该包含前两句
            assertTrue(summary.contains("第一句"));
            assertTrue(summary.contains("第二句"));
        }

        @Test
        @DisplayName("短文本应该原样返回")
        void shouldReturnOriginalForShortText() {
            String text = "只有一句话。";
            assertEquals(text, assetBinding.extractSummary(text, 3));
        }

        @Test
        @DisplayName("null 或空文本应该返回原值")
        void shouldReturnNullOrEmptyAsIs() {
            assertNull(assetBinding.extractSummary(null, 3));
            assertEquals("", assetBinding.extractSummary("", 3));
        }
    }

    @Nested
    @DisplayName("视频信息测试")
    class VideoInfoTests {

        @Test
        @DisplayName("应该返回视频基本信息")
        void shouldReturnBasicVideoInfo() {
            byte[] videoBytes = new byte[1024 * 1024]; // 1MB
            Map<String, Object> info = assetBinding.getVideoInfo(videoBytes);

            assertNotNull(info);
            assertEquals(1024 * 1024, info.get("sizeBytes"));
            assertEquals("1.00", info.get("sizeMB"));
        }

        @Test
        @DisplayName("应该检测 MP4 格式")
        void shouldDetectMp4Format() {
            // MP4 magic bytes at offset 4: ftyp
            byte[] mp4Bytes = new byte[12];
            mp4Bytes[4] = 0x66; // f
            mp4Bytes[5] = 0x74; // t
            mp4Bytes[6] = 0x79; // y
            mp4Bytes[7] = 0x70; // p

            Map<String, Object> info = assetBinding.getVideoInfo(mp4Bytes);
            assertEquals("mp4", info.get("format"));
        }
    }
}
