package com.actionow.project.dto.library;

import com.actionow.project.entity.Style;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共库风格响应
 *
 * @author Actionow
 */
@Data
public class LibraryStyleResponse {

    private String id;
    private String name;
    private String description;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishNote;
    private LocalDateTime createdAt;

    public static LibraryStyleResponse fromEntity(Style s) {
        LibraryStyleResponse r = new LibraryStyleResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setPublishedAt(s.getPublishedAt());
        r.setPublishNote(s.getPublishNote());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
