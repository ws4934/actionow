package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.inspiration.CreateSessionRequest;
import com.actionow.project.dto.inspiration.InspirationSessionResponse;
import com.actionow.project.dto.inspiration.UpdateSessionRequest;

/**
 * 灵感会话服务接口。
 *
 * <p><b>已 deprecated</b>：被 Asset + EntityRelation 统一流程取代，详见
 * {@link com.actionow.project.controller.InspirationController}。
 * 仅保留以维持现网功能；新代码请勿引入此接口的依赖。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
public interface InspirationSessionService {

    Page<InspirationSessionResponse> listSessions(String workspaceId, String userId,
                                                   Integer page, Integer size, String status);

    InspirationSessionResponse getSession(String sessionId);

    InspirationSessionResponse createSession(CreateSessionRequest request, String workspaceId, String userId);

    InspirationSessionResponse updateSession(String sessionId, UpdateSessionRequest request);

    void deleteSession(String sessionId);

    InspirationSessionResponse archiveSession(String sessionId);
}
