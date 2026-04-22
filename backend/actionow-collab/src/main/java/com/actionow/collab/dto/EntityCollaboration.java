package com.actionow.collab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实体协作状态
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityCollaboration {

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 查看者列表
     */
    private List<Collaborator> viewers;

    /**
     * 编辑者 (最多一个)
     */
    private Collaborator editor;
}
