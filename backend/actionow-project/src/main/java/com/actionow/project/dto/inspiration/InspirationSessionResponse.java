package com.actionow.project.dto.inspiration;

import com.actionow.project.entity.InspirationSession;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 灵感会话响应
 *
 * @author Actionow
 */
@Data
public class InspirationSessionResponse {

    private String id;
    private String workspaceId;
    private String title;
    private String coverUrl;
    private Integer recordCount;
    private BigDecimal totalCredits;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;

    public static InspirationSessionResponse fromEntity(InspirationSession session) {
        InspirationSessionResponse response = new InspirationSessionResponse();
        response.setId(session.getId());
        response.setWorkspaceId(session.getWorkspaceId());
        response.setTitle(session.getTitle());
        response.setCoverUrl(session.getCoverUrl());
        response.setRecordCount(session.getRecordCount());
        response.setTotalCredits(session.getTotalCredits());
        response.setStatus(session.getStatus());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        response.setLastActiveAt(session.getLastActiveAt());
        return response;
    }
}
