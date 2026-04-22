package com.actionow.collab.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentMention {
    private String type;
    private String id;
    private String name;
    private Integer offset;
    private Integer length;
}
