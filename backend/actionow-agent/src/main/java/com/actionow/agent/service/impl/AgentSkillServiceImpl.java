package com.actionow.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.dto.request.SkillCreateRequest;
import com.actionow.agent.dto.request.SkillUpdateRequest;
import com.actionow.agent.dto.response.SkillImportResult;
import com.actionow.agent.dto.response.SkillResponse;
import com.actionow.agent.entity.AgentSkillEntity;
import com.actionow.agent.mapper.AgentSkillMapper;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.service.AgentSkillService;
import com.actionow.agent.skill.SkillPackageLoader;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Skill 管理服务实现
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>OSS ZIP 包模式（推荐）：管理员将 skills 打包上传至 TOS/OSS，配置 {@code SKILL_PACKAGE_URL}
 *       后，服务启动及 reload 时自动下载并 upsert 到数据库。</li>
 *   <li>DB CRUD 模式（备选）：通过 Admin API 直接管理数据库中的 Skill 定义。</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillServiceImpl implements AgentSkillService {

    private static final String CHANNEL_SKILL_RELOAD = "agent:skill:reload";

    private final AgentSkillMapper skillMapper;
    private final DatabaseSkillRegistry skillRegistry;
    private final SaaAgentFactory agentFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final SkillPackageLoader skillPackageLoader;
    private final ProjectToolRegistry toolRegistry;

    /** OSS ZIP 包地址（可选）。若设置则启动时及 reload 时自动下载并导入。 */
    @Value("${actionow.skill.package-url:}")
    private String skillPackageUrl;

    /**
     * 服务启动时，若配置了 SKILL_PACKAGE_URL，自动从 OSS 下载并导入 Skill 包。
     * 下载失败不阻断启动，降级为 DB 中已有数据。
     */
    @PostConstruct
    public void onStartup() {
        if (skillPackageUrl == null || skillPackageUrl.isBlank()) {
            log.info("未配置 SKILL_PACKAGE_URL，跳过 Skill 包自动下载（使用 DB 中已有 Skill）");
            return;
        }
        log.info("检测到 SKILL_PACKAGE_URL，开始启动时 Skill 包导入...");
        try {
            int count = skillPackageLoader.downloadAndApply(skillPackageUrl);
            log.info("启动时 Skill 包导入完成：共导入 {} 个 Skill", count);
        } catch (Exception e) {
            log.warn("启动时 Skill 包下载失败（使用 DB 中已有数据）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse create(SkillCreateRequest request) {
        AgentSkillEntity existing = skillMapper.selectByName(request.getName());
        if (existing != null) {
            throw new BusinessException("Skill 已存在: " + request.getName());
        }

        validateGroupedToolIds(request.getGroupedToolIds());

        AgentSkillEntity entity = new AgentSkillEntity();
        entity.setName(request.getName());
        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setContent(request.getContent());
        entity.setGroupedToolIds(request.getGroupedToolIds());
        entity.setOutputSchema(request.getOutputSchema());
        entity.setTags(request.getTags());
        entity.setReferences(request.getReferences());
        entity.setExamples(request.getExamples());
        entity.setEnabled(true);
        entity.setVersion(1);
        entity.setScope(request.getScope() != null ? request.getScope() : "SYSTEM");
        entity.setWorkspaceId(request.getWorkspaceId());
        entity.setDeleted(0);

        skillMapper.insert(entity);
        log.info("创建 Skill: name={}, id={}", entity.getName(), entity.getId());

        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse update(String name, SkillUpdateRequest request) {
        AgentSkillEntity entity = requireByName(name);

        if (request.getGroupedToolIds() != null) {
            validateGroupedToolIds(request.getGroupedToolIds());
        }

        if (request.getDisplayName() != null) entity.setDisplayName(request.getDisplayName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getContent() != null) entity.setContent(request.getContent());
        if (request.getGroupedToolIds() != null) entity.setGroupedToolIds(request.getGroupedToolIds());
        if (request.getOutputSchema() != null) entity.setOutputSchema(request.getOutputSchema());
        if (request.getTags() != null) entity.setTags(request.getTags());
        if (request.getReferences() != null) entity.setReferences(request.getReferences());
        if (request.getExamples() != null) entity.setExamples(request.getExamples());

        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(LocalDateTime.now());

        skillMapper.updateById(entity);
        log.info("更新 Skill: name={}, newVersion={}", name, entity.getVersion());

        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String name) {
        AgentSkillEntity entity = requireByName(name);
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        log.info("软删除 Skill: name={}", name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse toggle(String name) {
        AgentSkillEntity entity = requireByName(name);
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        log.info("切换 Skill 状态: name={}, enabled={}", name, entity.getEnabled());
        return toResponse(entity, true);
    }

    @Override
    public SkillResponse getByName(String name) {
        AgentSkillEntity entity = requireByName(name);
        return toResponse(entity, true);
    }

    @Override
    public SkillResponse getByNameForWorkspace(String name, String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException("workspaceId 不能为空");
        }
        AgentSkillEntity entity = skillMapper.selectVisibleByName(name, workspaceId);
        if (entity == null) {
            throw new BusinessException("当前工作空间不可见的 Skill: " + name);
        }
        String scope = entity.getScope();
        if ("WORKSPACE".equals(scope) && !workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException("当前工作空间不可见的 Skill: " + name);
        }
        return toResponse(entity, true);
    }

    @Override
    public PageResult<SkillResponse> findPage(int page, int size, String keyword) {
        LambdaQueryWrapper<AgentSkillEntity> wrapper = new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getDeleted, 0)
                .orderByAsc(AgentSkillEntity::getName);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AgentSkillEntity::getName, keyword)
                    .or().like(AgentSkillEntity::getDescription, keyword));
        }

        var pageResult = skillMapper.selectPage(new Page<>(page, size), wrapper);
        var records = pageResult.getRecords().stream()
                .map(e -> toResponse(e, false))
                .toList();

        return PageResult.of((long) page, (long) size, pageResult.getTotal(), records);
    }

    @Override
    public int reload() {
        // 若配置了 OSS ZIP 包地址，先重新下载并导入最新包
        if (skillPackageUrl != null && !skillPackageUrl.isBlank()) {
            try {
                int imported = skillPackageLoader.downloadAndApply(skillPackageUrl);
                log.info("Reload：从 OSS 导入 {} 个 Skill", imported);
            } catch (Exception e) {
                log.error("Reload：Skill 包下载失败，使用 DB 中已有数据: {}", e.getMessage());
            }
        }

        // ① 先更新本地内存缓存（确保 factory 重建时读到最新 Skill 数据）
        skillRegistry.reload();

        // ② 使 Factory 缓存失效（下次请求将用新 registry 数据重建）
        agentFactory.invalidateCache();

        // ③ 广播给其他实例（其他实例收到后各自 reload + invalidateCache）
        stringRedisTemplate.convertAndSend(CHANNEL_SKILL_RELOAD, String.valueOf(System.currentTimeMillis()));

        int count = skillRegistry.size();
        log.info("Skill 缓存已重载，共 {} 个 Skill", count);
        return count;
    }

    // ==================== 私有方法 ====================

    private AgentSkillEntity requireByName(String name) {
        AgentSkillEntity entity = skillMapper.selectByName(name);
        if (entity == null) {
            throw new BusinessException("Skill 不存在: " + name);
        }
        return entity;
    }

    private AgentSkillEntity requireByNameAndWorkspace(String name, String workspaceId) {
        AgentSkillEntity entity = skillMapper.selectByNameAndWorkspace(name, workspaceId);
        if (entity == null) {
            throw new BusinessException("当前工作空间不存在 Skill: " + name);
        }
        return entity;
    }

    // ==================== Workspace 级 Skill 管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse createForWorkspace(SkillCreateRequest request, String workspaceId, String userId) {
        AgentSkillEntity existing = skillMapper.selectByNameAndWorkspace(request.getName(), workspaceId);
        if (existing != null) {
            throw new BusinessException("当前工作空间已存在同名 Skill: " + request.getName());
        }

        validateGroupedToolIds(request.getGroupedToolIds());

        AgentSkillEntity entity = new AgentSkillEntity();
        entity.setName(request.getName());
        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setContent(request.getContent());
        entity.setGroupedToolIds(request.getGroupedToolIds());
        entity.setOutputSchema(request.getOutputSchema());
        entity.setTags(request.getTags());
        entity.setReferences(request.getReferences());
        entity.setExamples(request.getExamples());
        entity.setEnabled(true);
        entity.setVersion(1);
        entity.setScope("WORKSPACE");
        entity.setWorkspaceId(workspaceId);
        entity.setCreatorId(userId);
        entity.setDeleted(0);

        skillMapper.insert(entity);
        log.info("创建 WORKSPACE Skill: name={}, workspaceId={}, creatorId={}", entity.getName(), workspaceId, userId);

        reload();
        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse updateForWorkspace(String name, SkillUpdateRequest request, String workspaceId) {
        AgentSkillEntity entity = requireByNameAndWorkspace(name, workspaceId);

        if (request.getGroupedToolIds() != null) {
            validateGroupedToolIds(request.getGroupedToolIds());
        }

        if (request.getDisplayName() != null) entity.setDisplayName(request.getDisplayName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getContent() != null) entity.setContent(request.getContent());
        if (request.getGroupedToolIds() != null) entity.setGroupedToolIds(request.getGroupedToolIds());
        if (request.getOutputSchema() != null) entity.setOutputSchema(request.getOutputSchema());
        if (request.getTags() != null) entity.setTags(request.getTags());
        if (request.getReferences() != null) entity.setReferences(request.getReferences());
        if (request.getExamples() != null) entity.setExamples(request.getExamples());

        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(LocalDateTime.now());

        skillMapper.updateById(entity);
        log.info("更新 WORKSPACE Skill: name={}, workspaceId={}, newVersion={}", name, workspaceId, entity.getVersion());

        reload();
        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForWorkspace(String name, String workspaceId) {
        AgentSkillEntity entity = requireByNameAndWorkspace(name, workspaceId);
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        log.info("软删除 WORKSPACE Skill: name={}, workspaceId={}", name, workspaceId);

        reload();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillResponse toggleForWorkspace(String name, String workspaceId) {
        AgentSkillEntity entity = requireByNameAndWorkspace(name, workspaceId);
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        log.info("切换 WORKSPACE Skill 状态: name={}, workspaceId={}, enabled={}", name, workspaceId, entity.getEnabled());

        reload();
        return toResponse(entity, true);
    }

    @Override
    public PageResult<SkillResponse> findPageForWorkspace(int page, int size, String keyword, String workspaceId) {
        LambdaQueryWrapper<AgentSkillEntity> wrapper = new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getDeleted, 0)
                .and(w -> w
                        .eq(AgentSkillEntity::getScope, "SYSTEM")
                        .or(inner -> inner
                                .eq(AgentSkillEntity::getScope, "WORKSPACE")
                                .eq(AgentSkillEntity::getWorkspaceId, workspaceId)))
                .orderByAsc(AgentSkillEntity::getName);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AgentSkillEntity::getName, keyword)
                    .or().like(AgentSkillEntity::getDescription, keyword));
        }

        var pageResult = skillMapper.selectPage(new Page<>(page, size), wrapper);
        var records = pageResult.getRecords().stream()
                .map(e -> toResponse(e, false))
                .toList();

        return PageResult.of((long) page, (long) size, pageResult.getTotal(), records);
    }

    // ==================== Skill 包导入 ====================

    @Override
    public SkillImportResult importPackage(byte[] zipBytes) {
        SkillImportResult result = skillPackageLoader.applyFromBytes(zipBytes, "SYSTEM", null, null);
        if (result.getSuccess() > 0) {
            reload();
        }
        return result;
    }

    @Override
    public SkillImportResult importPackageForWorkspace(byte[] zipBytes, String workspaceId, String userId) {
        SkillImportResult result = skillPackageLoader.applyFromBytes(zipBytes, "WORKSPACE", workspaceId, userId);
        if (result.getSuccess() > 0) {
            reload();
        }
        return result;
    }

    /**
     * 校验 groupedToolIds 中的工具 ID 是否都已注册
     */
    private void validateGroupedToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return;
        List<String> invalid = toolIds.stream()
                .filter(id -> toolRegistry.getProjectTool(id).isEmpty())
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException("以下工具 ID 不存在或未注册: " + invalid);
        }
    }

    private SkillResponse toResponse(AgentSkillEntity entity, boolean includeContent) {
        return SkillResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .content(includeContent ? entity.getContent() : null)
                .groupedToolIds(entity.getGroupedToolIds())
                .outputSchema(entity.getOutputSchema())
                .tags(entity.getTags())
                .references(entity.getReferences())
                .examples(entity.getExamples())
                .enabled(entity.getEnabled())
                .version(entity.getVersion())
                .scope(entity.getScope())
                .workspaceId(entity.getWorkspaceId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
