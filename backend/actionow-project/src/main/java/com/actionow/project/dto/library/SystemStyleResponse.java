package com.actionow.project.dto.library;

import com.actionow.project.entity.Style;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统管理员视图风格响应
 *
 * @author Actionow
 */
@Data
public class SystemStyleResponse {

    private String id;
    private String name;
    private String description;
    private String scope;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishNote;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SystemStyleResponse fromEntity(Style s) {
        SystemStyleResponse r = new SystemStyleResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setScope(s.getScope());
        r.setPublishedAt(s.getPublishedAt());
        r.setPublishedBy(s.getPublishedBy());
        r.setPublishNote(s.getPublishNote());
        r.setCreatedAt(s.getCreatedAt());
        r.setCreatedBy(s.getCreatedBy());
        return r;
    }
}
