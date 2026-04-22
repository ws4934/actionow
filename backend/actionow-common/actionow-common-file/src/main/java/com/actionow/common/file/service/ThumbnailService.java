package com.actionow.common.file.service;

import java.io.InputStream;

/**
 * 缩略图服务接口
 *
 * @author Actionow
 */
public interface ThumbnailService {

    /**
     * 默认缩略图宽度
     */
    int DEFAULT_WIDTH = 300;

    /**
     * 默认缩略图高度
     */
    int DEFAULT_HEIGHT = 300;

    /**
     * 生成图片缩略图
     *
     * @param inputStream 原图输入流
     * @param mimeType    原图MIME类型
     * @param width       缩略图宽度
     * @param height      缩略图高度
     * @return 缩略图字节数组
     */
    byte[] generateImageThumbnail(InputStream inputStream, String mimeType, int width, int height);

    /**
     * 生成图片缩略图（默认尺寸 300x300）
     *
     * @param inputStream 原图输入流
     * @param mimeType    原图MIME类型
     * @return 缩略图字节数组
     */
    default byte[] generateImageThumbnail(InputStream inputStream, String mimeType) {
        return generateImageThumbnail(inputStream, mimeType, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 判断是否支持生成缩略图
     *
     * @param mimeType MIME类型
     * @return 是否支持
     */
    boolean supportsThumbnail(String mimeType);

    /**
     * 判断是否支持视频缩略图
     *
     * @param mimeType MIME类型
     * @return 是否支持
     */
    boolean supportsVideoThumbnail(String mimeType);

    /**
     * 从视频文件生成缩略图（提取指定时间的帧）
     *
     * @param videoInputStream 视频输入流
     * @param mimeType         视频MIME类型
     * @param timeOffsetSeconds 提取帧的时间点（秒），默认为1秒
     * @param width            缩略图宽度
     * @param height           缩略图高度
     * @return 缩略图字节数组，如果生成失败返回 null
     */
    byte[] generateVideoThumbnail(java.io.InputStream videoInputStream, String mimeType,
                                   double timeOffsetSeconds, int width, int height);

    /**
     * 从视频文件生成缩略图（默认提取第1秒的帧，默认尺寸 300x300）
     *
     * @param videoInputStream 视频输入流
     * @param mimeType         视频MIME类型
     * @return 缩略图字节数组
     */
    default byte[] generateVideoThumbnail(java.io.InputStream videoInputStream, String mimeType) {
        return generateVideoThumbnail(videoInputStream, mimeType, 1.0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
}
