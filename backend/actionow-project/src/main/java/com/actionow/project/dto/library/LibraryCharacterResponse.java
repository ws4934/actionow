package com.actionow.project.dto.library;

import com.actionow.project.entity.Character;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共库角色响应（普通租户浏览用）
 *
 * @author Actionow
 */
@Data
public class LibraryCharacterResponse {

    private String id;
    private String name;
    private String description;
    private Integer age;
    private String gender;
    private String characterType;
    private String coverUrl;
    private LocalDateTime publishedAt;
    private String publishNote;
    private LocalDateTime createdAt;

    public static LibraryCharacterResponse fromEntity(Character c) {
        LibraryCharacterResponse r = new LibraryCharacterResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        r.setAge(c.getAge());
        r.setGender(c.getGender());
        r.setCharacterType(c.getCharacterType());
        r.setPublishedAt(c.getPublishedAt());
        r.setPublishNote(c.getPublishNote());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }
}
