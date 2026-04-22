package com.actionow.task.service;

import com.actionow.common.core.result.Result;
import com.actionow.task.dto.AvailableProviderResponse;
import com.actionow.task.dto.TaskResponse;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.feign.ProjectFeignClient;
import com.actionow.task.feign.UserBasicInfo;
import com.actionow.task.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务响应名称回填服务
 * 批量查询并回填 scriptName、creatorName、providerName
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskResponseEnricher {

    private final UserFeignClient userFeignClient;
    private final ProjectFeignClient projectFeignClient;
    private final AiFeignClient aiFeignClient;

    /**
     * 批量回填任务响应的名称字段
     * 收集去重的 ID，批量 RPC 查询后回填
     */
    public void enrich(List<TaskResponse> responses) {
        if (CollectionUtils.isEmpty(responses)) {
            return;
        }

        enrichCreatorNames(responses);
        enrichScriptNames(responses);
        enrichProviderNames(responses);
    }

    /**
     * 回填单个任务响应的名称字段
     */
    public void enrich(TaskResponse response) {
        if (response == null) {
            return;
        }
        enrich(List.of(response));
    }

    private void enrichCreatorNames(List<TaskResponse> responses) {
        Set<String> creatorIds = responses.stream()
                .map(TaskResponse::getCreatorId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (creatorIds.isEmpty()) {
            return;
        }

        try {
            Result<Map<String, UserBasicInfo>> result =
                    userFeignClient.batchGetUserBasicInfo(new ArrayList<>(creatorIds));
            if (result.isSuccess() && result.getData() != null) {
                Map<String, UserBasicInfo> userMap = result.getData();
                responses.forEach(r -> {
                    if (StringUtils.hasText(r.getCreatorId())) {
                        UserBasicInfo user = userMap.get(r.getCreatorId());
                        if (user != null) {
                            r.setCreatorName(StringUtils.hasText(user.getNickname())
                                    ? user.getNickname() : user.getUsername());
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.warn("批量查询用户名称失败: {}", e.getMessage());
        }
    }

    private void enrichScriptNames(List<TaskResponse> responses) {
        Set<String> scriptIds = responses.stream()
                .map(TaskResponse::getScriptId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (scriptIds.isEmpty()) {
            return;
        }

        try {
            Result<List<Map<String, Object>>> result =
                    projectFeignClient.batchGetScripts(new ArrayList<>(scriptIds));
            if (result.isSuccess() && result.getData() != null) {
                Map<String, String> scriptNameMap = result.getData().stream()
                        .filter(m -> m.get("id") != null && m.get("name") != null)
                        .collect(Collectors.toMap(
                                m -> (String) m.get("id"),
                                m -> (String) m.get("name"),
                                (a, b) -> a
                        ));
                responses.forEach(r -> {
                    if (StringUtils.hasText(r.getScriptId())) {
                        r.setScriptName(scriptNameMap.get(r.getScriptId()));
                    }
                });
            }
        } catch (Exception e) {
            log.warn("批量查询剧本名称失败: {}", e.getMessage());
        }
    }

    private void enrichProviderNames(List<TaskResponse> responses) {
        Set<String> providerIds = responses.stream()
                .map(TaskResponse::getProviderId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (providerIds.isEmpty()) {
            return;
        }

        Map<String, String> providerNameMap = new HashMap<>();
        for (String providerId : providerIds) {
            try {
                Result<AvailableProviderResponse> result = aiFeignClient.getProviderDetail(providerId);
                if (result.isSuccess() && result.getData() != null) {
                    providerNameMap.put(providerId, result.getData().getName());
                }
            } catch (Exception e) {
                log.warn("查询模型提供商名称失败: providerId={}, error={}", providerId, e.getMessage());
            }
        }

        if (!providerNameMap.isEmpty()) {
            responses.forEach(r -> {
                if (StringUtils.hasText(r.getProviderId())) {
                    r.setProviderName(providerNameMap.get(r.getProviderId()));
                }
            });
        }
    }
}
