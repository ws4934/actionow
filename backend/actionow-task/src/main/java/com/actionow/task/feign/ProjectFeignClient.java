package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目服务 Feign 客户端
 * 用于批量查询剧本信息、Scope 展开（集/分镜/角色/场景/道具列表）
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-project", contextId = "taskProjectFeignClient",
        path = "/internal/project", fallbackFactory = ProjectFeignClientFallbackFactory.class)
public interface ProjectFeignClient {

    /**
     * 批量获取剧本信息
     */
    @PostMapping("/scripts/batch-get")
    Result<List<Map<String, Object>>> batchGetScripts(@RequestBody List<String> ids);

    // ==================== Scope 展开: Episode ====================

    /**
     * 列出剧本下的所有集
     */
    @GetMapping("/episodes/script/{scriptId}")
    Result<List<Map<String, Object>>> listEpisodesByScript(
            @PathVariable("scriptId") String scriptId,
            @RequestParam(value = "limit", required = false) Integer limit);

    // ==================== Scope 展开: Storyboard ====================

    /**
     * 列出某集下的所有分镜
     */
    @GetMapping("/storyboards/episode/{episodeId}")
    Result<List<Map<String, Object>>> listStoryboardsByEpisode(
            @PathVariable("episodeId") String episodeId,
            @RequestParam(value = "limit", required = false) Integer limit);

    // ==================== Scope 展开: Character / Scene / Prop ====================

    /**
     * 列出剧本下可用的角色
     */
    @GetMapping("/characters/available/{scriptId}")
    Result<List<Map<String, Object>>> listAvailableCharacters(
            @PathVariable("scriptId") String scriptId,
            @RequestParam(value = "limit", required = false) Integer limit);

    /**
     * 列出剧本下可用的场景
     */
    @GetMapping("/scenes/available/{scriptId}")
    Result<List<Map<String, Object>>> listAvailableScenes(
            @PathVariable("scriptId") String scriptId,
            @RequestParam(value = "limit", required = false) Integer limit);

    /**
     * 列出剧本下可用的道具
     */
    @GetMapping("/props/available/{scriptId}")
    Result<List<Map<String, Object>>> listAvailableProps(
            @PathVariable("scriptId") String scriptId,
            @RequestParam(value = "limit", required = false) Integer limit);
}
