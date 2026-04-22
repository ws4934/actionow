package com.actionow.project.dto.library;

import com.actionow.project.entity.Character;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员视图角色响应（含 scope，可见草稿和已发布）
 *
 * @author Actionow
 */
@Data
public class SystemCharacterResponse {

    private String id;
    private String name;
    private String description;
    private Integer age;
    private String gender;
    private String characterType;
    /** WORKSPACE=草稿（待发布），SYSTEM=已发布 */
    private String scope;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String publishNote;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SystemCharacterResponse fromEntity(Character c) {
        SystemCharacterResponse r = new SystemCharacterResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        r.setAge(c.getAge());
        r.setGender(c.getGender());
        r.setCharacterType(c.getCharacterType());
        r.setScope(c.getScope());
        r.setPublishedAt(c.getPublishedAt());
        r.setPublishedBy(c.getPublishedBy());
        r.setPublishNote(c.getPublishNote());
        r.setCreatedAt(c.getCreatedAt());
        r.setCreatedBy(c.getCreatedBy());
        return r;
    }
}
