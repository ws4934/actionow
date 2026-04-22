package com.actionow.collab.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_comment_attachment", autoResultMap = true)
public class CommentAttachment implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String commentId;

    private String assetId;

    private String assetType;

    private String fileName;

    private String fileUrl;

    private String thumbnailUrl;

    private Long fileSize;

    private String mimeType;

    @TableField(value = "meta_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaInfo;

    private Integer sequence;

    private LocalDateTime createdAt;
}
