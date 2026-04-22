package com.actionow.common.file.event;

/**
 * 文件事件常量
 *
 * @author Actionow
 */
public final class FileEventConstants {

    private FileEventConstants() {
    }

    /**
     * 文件上传完成事件路由键
     */
    public static final String FILE_UPLOADED_ROUTING_KEY = "file.uploaded";

    /**
     * 缩略图生成完成事件路由键
     */
    public static final String THUMBNAIL_GENERATED_ROUTING_KEY = "file.thumbnail.generated";

    /**
     * 文件事件交换机
     */
    public static final String FILE_EVENT_EXCHANGE = "file.event.exchange";

    /**
     * 缩略图生成队列
     */
    public static final String THUMBNAIL_GENERATION_QUEUE = "file.thumbnail.generation.queue";
}
