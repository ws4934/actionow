package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 灵感记录资产关联实体。
 *
 * <p><b>已 deprecated</b>：与 t_asset 重复字段（url/width/height/duration），未来由
 * EntityRelation(SESSION→ASSET) 取代，详见
 * {@link com.actionow.project.controller.InspirationController}。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_inspiration_record_asset", autoResultMap = true)
public class InspirationRecordAsset extends BaseEntity {

    @TableField("record_id")
    private String recordId;

    @TableField("asset_id")
    private String assetId;

    @TableField("asset_type")
    private String assetType;

    private String url;

    @TableField("thumbnail_url")
    private String thumbnailUrl;

    private Integer width;

    private Integer height;

    private Double duration;

    @TableField("mime_type")
    private String mimeType;

    @TableField("file_size")
    private Long fileSize;
}
