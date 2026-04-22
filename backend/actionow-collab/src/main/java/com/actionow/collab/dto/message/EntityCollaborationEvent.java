package com.actionow.collab.dto.message;

import com.actionow.collab.dto.Collaborator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实体协作状态事件
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityCollaborationEvent {

    /**
     * 剧本ID
     */
    private String scriptId;

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
     * 编辑者
     */
    private Collaborator editor;
}
