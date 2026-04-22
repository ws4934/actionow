package com.actionow.agent.tool.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.tool.dto.AgentToolAccessRequest;
import com.actionow.agent.tool.dto.AgentToolAccessResponse;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.entity.AgentToolAccess;
import com.actionow.agent.tool.mapper.AgentToolAccessMapper;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import com.actionow.agent.tool.service.AgentToolAccessService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工具访问权限服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolAccessServiceImpl implements AgentToolAccessService {

    private static final String CACHE_KEY_PREFIX = "agent:tool:access:";
    private static final String QUOTA_KEY_PREFIX = "agent:tool:quota:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration QUOTA_TTL = Duration.ofDays(2); // 多保留一天以防时区问题

    private final AgentToolAccessMapper mapper;
    private final ProjectToolRegistry toolRegistry;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentToolAccessResponse create(AgentToolAccessRequest request) {
        // 检查是否已存在
        AgentToolAccess existing = mapper.selectByAgentAndTool(
                request.getAgentType(), request.getToolCategory(), request.getToolId());
        if (existing != null) {
            throw new BusinessException("工具权限已存在");
        }

        AgentToolAccess access = new AgentToolAccess();
        mapRequestToEntity(request, access);

        mapper.insert(access);

        log.info("创建工具权限: id={}, agentType={}, toolId={}",
                access.getId(), access.getAgentType(), access.getToolId());

        evictCache(access.getAgentType());

        return AgentToolAccessResponse.fromEntity(access);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AgentToolAccessResponse> createBatch(List<AgentToolAccessRequest> requests) {
        List<AgentToolAccessResponse> results = new ArrayList<>();
        for (AgentToolAccessRequest request : requests) {
            try {
                results.add(create(request));
            } catch (BusinessException e) {
                log.warn("批量创建工具权限跳过: {}", e.getMessage());
            }
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentToolAccessResponse update(String id, AgentToolAccessRequest request) {
        AgentToolAccess access = mapper.selectById(id);
        if (access == null || access.getDeleted() != 0) {
            throw new BusinessException("工具权限不存在: " + id);
        }

        String oldAgentType = access.getAgentType();
        mapRequestToEntity(request, access);
        mapper.updateById(access);

        log.info("更新工具权限: id={}", id);

        evictCache(oldAgentType);
        if (!oldAgentType.equals(access.getAgentType())) {
            evictCache(access.getAgentType());
        }

        return AgentToolAccessResponse.fromEntity(access);
    }

    @Override
    public AgentToolAccessResponse getById(String id) {
        AgentToolAccess access = mapper.selectById(id);
        if (access == null || access.getDeleted() != 0) {
            throw new BusinessException("工具权限不存在: " + id);
        }
        return AgentToolAccessResponse.fromEntity(access);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AgentToolAccess access = mapper.selectById(id);
        if (access == null || access.getDeleted() != 0) {
            throw new BusinessException("工具权限不存在: " + id);
        }

        mapper.deleteById(id);

        log.info("删除工具权限: id={}", id);

        evictCache(access.getAgentType());
    }

    @Override
    public PageResult<AgentToolAccessResponse> findPage(Long current, Long size,
                                                         String agentType, String toolCategory,
                                                         String toolId, Boolean enabled) {
        Page<AgentToolAccess> page = new Page<>(current, size);
        IPage<AgentToolAccess> resultPage = mapper.selectPage(page, agentType, toolCategory, toolId, enabled);

        List<AgentToolAccessResponse> records = resultPage.getRecords().stream()
                .map(AgentToolAccessResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(current, size, resultPage.getTotal(), records);
    }

    @Override
    public List<AgentToolAccessResponse> getByAgentType(String agentType) {
        return mapper.selectEnabledByAgentType(agentType).stream()
                .map(AgentToolAccessResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentToolAccessResponse> getByAgentTypeAndCategory(String agentType, String toolCategory) {
        return mapper.selectByAgentTypeAndCategory(agentType, toolCategory).stream()
                .map(AgentToolAccessResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentToolAccessResponse> getByToolId(String toolId) {
        return mapper.selectByToolId(toolId).stream()
                .map(AgentToolAccessResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(String id, Boolean enabled) {
        AgentToolAccess access = mapper.selectById(id);
        if (access == null || access.getDeleted() != 0) {
            throw new BusinessException("工具权限不存在: " + id);
        }

        access.setEnabled(enabled);
        mapper.updateById(access);

        log.info("切换工具权限状态: id={}, enabled={}", id, enabled);

        evictCache(access.getAgentType());
    }

    @Override
    public boolean hasAccess(String agentType, String toolCategory, String toolId) {
        return toolRegistry.isToolAvailable(agentType, toolCategory, toolId);
    }

    @Override
    public boolean checkQuota(String agentType, String toolCategory, String toolId, String userId) {
        AgentToolAccess access = mapper.selectByAgentAndTool(agentType, toolCategory, toolId);
        if (access == null) {
            return false;
        }

        // -1 表示无限制
        if (access.getDailyQuota() == null || access.getDailyQuota() < 0) {
            return true;
        }

        // 查询用户当天的调用次数
        String quotaKey = buildQuotaKey(userId, toolCategory, toolId);
        Long currentCount = getQuotaCount(quotaKey);

        return currentCount < access.getDailyQuota();
    }

    /**
     * 增加工具调用计数
     *
     * @param userId       用户 ID
     * @param toolCategory 工具类别
     * @param toolId       工具 ID
     * @return 当前调用次数
     */
    @Override
    public long incrementQuotaCount(String userId, String toolCategory, String toolId) {
        String quotaKey = buildQuotaKey(userId, toolCategory, toolId);
        Long count = redisTemplate.opsForValue().increment(quotaKey);
        if (count != null && count == 1) {
            // 首次计数，设置过期时间
            redisTemplate.expire(quotaKey, QUOTA_TTL);
        }
        return count != null ? count : 0;
    }

    /**
     * 构建配额计数 Key
     */
    private String buildQuotaKey(String userId, String toolCategory, String toolId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        return QUOTA_KEY_PREFIX + userId + ":" + toolCategory + ":" + toolId + ":" + today;
    }

    /**
     * 获取当前配额计数
     */
    private long getQuotaCount(String quotaKey) {
        Object value = redisTemplate.opsForValue().get(quotaKey);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<ToolInfo> getAvailableTools(String agentType, String userId) {
        List<ToolInfo> tools = toolRegistry.getToolsForAgent(agentType);
        markAvailability(agentType, userId, tools);
        return tools;
    }

    @Override
    public List<ToolInfo> getAvailableTools(String agentType, String userId, List<String> toolIds) {
        List<ToolInfo> tools = toolRegistry.getProjectTools(toolIds);
        markAvailability(agentType, userId, tools);
        return tools;
    }

    private void markAvailability(String agentType, String userId, List<ToolInfo> tools) {
        for (ToolInfo tool : tools) {
            boolean accessAllowed = hasAccess(agentType, tool.getCategory(), tool.getToolId());
            if (!accessAllowed) {
                tool.setAvailable(false);
                tool.setAccessMode("DISABLED");
                continue;
            }
            boolean quotaOk = checkQuota(agentType, tool.getCategory(), tool.getToolId(), userId);
            if (!quotaOk) {
                tool.setAvailable(false);
                tool.setAccessMode("QUOTA_EXHAUSTED");
                log.info("工具 {} 当日配额已耗尽，构建期排除: agentType={}, userId={}",
                        tool.getToolId(), agentType, userId);
                continue;
            }
            tool.setAvailable(tool.isAvailable());
        }
    }

    @Override
    public void refreshCache() {
        log.info("刷新所有工具权限缓存");
    }

    /**
     * 映射请求到实体
     */
    private void mapRequestToEntity(AgentToolAccessRequest request, AgentToolAccess entity) {
        if (request.getAgentType() != null) {
            entity.setAgentType(request.getAgentType().toUpperCase());
        }
        if (request.getToolCategory() != null) {
            entity.setToolCategory(request.getToolCategory().toUpperCase());
        }
        if (request.getToolId() != null) {
            entity.setToolId(request.getToolId());
        }
        if (request.getToolName() != null) {
            entity.setToolName(request.getToolName());
        }
        if (request.getToolDescription() != null) {
            entity.setToolDescription(request.getToolDescription());
        }
        if (request.getAccessMode() != null) {
            entity.setAccessMode(request.getAccessMode().toUpperCase());
        } else if (entity.getAccessMode() == null) {
            entity.setAccessMode("FULL");
        }
        if (request.getDailyQuota() != null) {
            entity.setDailyQuota(request.getDailyQuota());
        } else if (entity.getDailyQuota() == null) {
            entity.setDailyQuota(-1);
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        } else if (entity.getPriority() == null) {
            entity.setPriority(0);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
    }

    /**
     * 清除缓存
     */
    private void evictCache(String agentType) {
        String cacheKey = CACHE_KEY_PREFIX + agentType;
        redisTemplate.delete(cacheKey);
    }
}
