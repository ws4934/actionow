package com.actionow.project.dto.library;

import com.actionow.project.entity.Prop;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统管理员视图道具响应
 *
 * @author Actionow
 */
@Data
public class SystemPropResponse {

    private String id;
    private String name;
    private String description;
    private String propType;
    private String scope;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishNote;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SystemPropResponse fromEntity(Prop p) {
        SystemPropResponse r = new SystemPropResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setPropType(p.getPropType());
        r.setScope(p.getScope());
        r.setPublishedAt(p.getPublishedAt());
        r.setPublishedBy(p.getPublishedBy());
        r.setPublishNote(p.getPublishNote());
        r.setCreatedAt(p.getCreatedAt());
        r.setCreatedBy(p.getCreatedBy());
        return r;
    }
}
