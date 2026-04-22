package com.actionow.project.dto.library;

import com.actionow.project.entity.Scene;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统管理员视图场景响应
 *
 * @author Actionow
 */
@Data
public class SystemSceneResponse {

    private String id;
    private String name;
    private String description;
    private String sceneType;
    private String scope;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishNote;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SystemSceneResponse fromEntity(Scene s) {
        SystemSceneResponse r = new SystemSceneResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setSceneType(s.getSceneType());
        r.setScope(s.getScope());
        r.setPublishedAt(s.getPublishedAt());
        r.setPublishedBy(s.getPublishedBy());
        r.setPublishNote(s.getPublishNote());
        r.setCreatedAt(s.getCreatedAt());
        r.setCreatedBy(s.getCreatedBy());
        return r;
    }
}
