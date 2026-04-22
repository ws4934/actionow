package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.inspiration.*;
import com.actionow.project.service.InspirationRecordService;
import com.actionow.project.service.InspirationSessionService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 灵感模块控制器
 * 提供灵感会话管理、生成记录查询、AI 生成提交等接口。
 *
 * <p><b>已 deprecated</b>：本子系统（Session/Record/RecordAsset 三表 + 独立前端页）
 * 已被 Asset + EntityRelation 统一流程取代。新功能请直接使用 AssetService 与
 * EntityRelationService。本子系统在前端独立"灵感"页下线前保留运行；切勿在新代码中
 * 引入对 InspirationXxx 的依赖。
 *
 * <p>详见 Kaizen 提案 #2（A 方案：冻结依赖 + 流量埋点观察）。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Slf4j
@RestController
@RequestMapping("/inspiration")
@RequiredArgsConstructor
public class InspirationController {

    private final InspirationSessionService sessionService;
    private final InspirationRecordService recordService;

    @PostConstruct
    void announceDeprecation() {
        log.warn("[DEPRECATED] InspirationController 已标记为 deprecated（替代方案：Asset + EntityRelation）。"
                + "本子系统仅维持运行，禁止新增依赖。流量观察请查看 APM/Actuator metrics。");
    }

    // ==================== 会话管理 ====================

    /**
     * 获取灵感会话列表（分页，按 lastActiveAt 降序）
     */
    @GetMapping("/sessions")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Page<InspirationSessionResponse>> listSessions(
            @RequestParam(name = "pageNum", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(sessionService.listSessions(workspaceId, userId, page, size, status));
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<InspirationSessionResponse> getSession(@PathVariable String sessionId) {
        return Result.success(sessionService.getSession(sessionId));
    }

    /**
     * 创建新灵感会话
     */
    @PostMapping("/sessions")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<InspirationSessionResponse> createSession(
            @RequestBody(required = false) @Valid CreateSessionRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        if (request == null) {
            request = new CreateSessionRequest();
        }
        return Result.success(sessionService.createSession(request, workspaceId, userId));
    }

    /**
     * 更新会话信息（标题）
     */
    @PatchMapping("/sessions/{sessionId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<InspirationSessionResponse> updateSession(
            @PathVariable String sessionId,
            @RequestBody @Valid UpdateSessionRequest request) {
        return Result.success(sessionService.updateSession(sessionId, request));
    }

    /**
     * 删除会话及其所有关联记录
     */
    @DeleteMapping("/sessions/{sessionId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.success();
    }

    /**
     * 归档会话
     */
    @PostMapping("/sessions/{sessionId}/archive")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<InspirationSessionResponse> archiveSession(@PathVariable String sessionId) {
        return Result.success(sessionService.archiveSession(sessionId));
    }

    // ==================== 生成记录 ====================

    /**
     * 获取会话下的生成记录列表（分页，按 createdAt 升序）
     */
    @GetMapping("/sessions/{sessionId}/records")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Page<InspirationRecordResponse>> listRecords(
            @PathVariable String sessionId,
            @RequestParam(name = "pageNum", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "50") Integer size) {
        return Result.success(recordService.listRecords(sessionId, page, size));
    }

    /**
     * 删除单条生成记录
     */
    @DeleteMapping("/sessions/{sessionId}/records/{recordId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteRecord(@PathVariable String sessionId,
                                      @PathVariable String recordId) {
        recordService.deleteRecord(sessionId, recordId);
        return Result.success();
    }

    // ==================== 生成提交 ====================

    /**
     * 提交一次自由生成任务
     */
    @PostMapping("/generate")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<InspirationGenerateResponse> generate(
            @RequestBody @Valid InspirationGenerateRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(recordService.submitGeneration(request, workspaceId, userId));
    }
}
