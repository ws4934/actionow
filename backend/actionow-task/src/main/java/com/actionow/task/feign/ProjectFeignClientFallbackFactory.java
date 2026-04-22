package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 项目服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ProjectFeignClientFallbackFactory implements FallbackFactory<ProjectFeignClient> {

    @Override
    public ProjectFeignClient create(Throwable cause) {
        log.error("调用项目服务失败: {}", cause.getMessage());
        return new ProjectFeignClient() {
            @Override
            public Result<List<Map<String, Object>>> batchGetScripts(List<String> ids) {
                log.warn("批量获取剧本信息降级: count={}", ids != null ? ids.size() : 0);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<List<Map<String, Object>>> listEpisodesByScript(String scriptId, Integer limit) {
                log.warn("列出剧本下集降级: scriptId={}", scriptId);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<List<Map<String, Object>>> listStoryboardsByEpisode(String episodeId, Integer limit) {
                log.warn("列出集下分镜降级: episodeId={}", episodeId);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<List<Map<String, Object>>> listAvailableCharacters(String scriptId, Integer limit) {
                log.warn("列出可用角色降级: scriptId={}", scriptId);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<List<Map<String, Object>>> listAvailableScenes(String scriptId, Integer limit) {
                log.warn("列出可用场景降级: scriptId={}", scriptId);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<List<Map<String, Object>>> listAvailableProps(String scriptId, Integer limit) {
                log.warn("列出可用道具降级: scriptId={}", scriptId);
                return Result.success(Collections.emptyList());
            }
        };
    }
}
