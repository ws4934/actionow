package com.actionow.project.dto.library;

import com.actionow.project.entity.Scene;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共库场景响应
 *
 * @author Actionow
 */
@Data
public class LibrarySceneResponse {

    private String id;
    private String name;
    private String description;
    private String sceneType;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishNote;
    private LocalDateTime createdAt;

    public static LibrarySceneResponse fromEntity(Scene s) {
        LibrarySceneResponse r = new LibrarySceneResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setSceneType(s.getSceneType());
        r.setPublishedAt(s.getPublishedAt());
        r.setPublishNote(s.getPublishNote());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
