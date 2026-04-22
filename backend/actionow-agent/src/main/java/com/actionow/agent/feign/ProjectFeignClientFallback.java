package com.actionow.agent.feign;

import org.springframework.stereotype.Component;

/**
 * Project Feign 客户端降级工厂
 * <p>
 * 所有方法返回 Result.fail("0709001", ...) — 由基类代理自动处理。
 * List 返回类型自动降级为空列表。
 *
 * @author Actionow
 */
@Component
public class ProjectFeignClientFallback extends AbstractFeignFallbackFactory<ProjectFeignClient> {

    @Override
    protected String serviceName() {
        return "Project 服务";
    }

    @Override
    protected String errorCode() {
        return "0709001";
    }
}
