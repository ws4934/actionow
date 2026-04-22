package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体输出 DTO
 * 包含媒体类型和媒体项列表
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaOutput {

    /**
     * 媒体类型
     * 必填
     */
    private MediaType mediaType;

    /**
     * 媒体项列表（支持批量）
     * 必填，至少包含一个元素
     */
    @Builder.Default
    private List<MediaItem> items = new ArrayList<>();

    // ==================== 工厂方法 ====================

    /**
     * 创建单个图片输出
     */
    public static MediaOutput singleImage(String fileUrl, String mimeType) {
        return MediaOutput.builder()
                .mediaType(MediaType.IMAGE)
                .items(List.of(MediaItem.builder()
                        .fileUrl(fileUrl)
                        .mimeType(mimeType)
                        .build()))
                .build();
    }

    /**
     * 创建单个视频输出
     */
    public static MediaOutput singleVideo(String fileUrl, String mimeType) {
        return MediaOutput.builder()
                .mediaType(MediaType.VIDEO)
                .items(List.of(MediaItem.builder()
                        .fileUrl(fileUrl)
                        .mimeType(mimeType)
                        .build()))
                .build();
    }

    /**
     * 创建单个音频输出
     */
    public static MediaOutput singleAudio(String fileUrl, String mimeType) {
        return MediaOutput.builder()
                .mediaType(MediaType.AUDIO)
                .items(List.of(MediaItem.builder()
                        .fileUrl(fileUrl)
                        .mimeType(mimeType)
                        .build()))
                .build();
    }

    /**
     * 添加媒体项
     */
    public MediaOutput addItem(MediaItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        return this;
    }

    /**
     * 获取第一个媒体项（单个输出时使用）
     */
    public MediaItem getFirstItem() {
        return items != null && !items.isEmpty() ? items.get(0) : null;
    }

    /**
     * 是否为批量输出
     */
    public boolean isBatch() {
        return items != null && items.size() > 1;
    }
}
