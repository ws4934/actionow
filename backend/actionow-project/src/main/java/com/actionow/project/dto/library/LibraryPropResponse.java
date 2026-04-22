package com.actionow.project.dto.library;

import com.actionow.project.entity.Prop;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共库道具响应
 *
 * @author Actionow
 */
@Data
public class LibraryPropResponse {

    private String id;
    private String name;
    private String description;
    private String propType;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishNote;
    private LocalDateTime createdAt;

    public static LibraryPropResponse fromEntity(Prop p) {
        LibraryPropResponse r = new LibraryPropResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setPropType(p.getPropType());
        r.setPublishedAt(p.getPublishedAt());
        r.setPublishNote(p.getPublishNote());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }
}
