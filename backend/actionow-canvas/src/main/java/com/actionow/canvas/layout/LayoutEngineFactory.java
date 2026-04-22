package com.actionow.canvas.layout;

import com.actionow.canvas.constant.CanvasConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 布局引擎工厂
 * 管理所有布局引擎实例，根据策略名称获取对应引擎
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayoutEngineFactory {

    private final List<LayoutEngine> layoutEngines;
    private final Map<String, LayoutEngine> engineMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (LayoutEngine engine : layoutEngines) {
            engineMap.put(engine.getStrategy(), engine);
            log.debug("注册布局引擎: strategy={}", engine.getStrategy());
        }
        log.info("布局引擎工厂初始化完成: engineCount={}", engineMap.size());
    }

    /**
     * 根据策略获取布局引擎
     *
     * @param strategy 布局策略名称
     * @return 布局引擎，如果策略不存在则返回默认的 GRID 引擎
     */
    public LayoutEngine getEngine(String strategy) {
        LayoutEngine engine = engineMap.get(strategy);
        if (engine == null) {
            log.warn("未找到布局引擎: strategy={}, 使用默认 GRID 引擎", strategy);
            return engineMap.get(CanvasConstants.LayoutStrategy.GRID);
        }
        return engine;
    }

    /**
     * 检查策略是否支持
     */
    public boolean isSupported(String strategy) {
        return engineMap.containsKey(strategy);
    }

    /**
     * 获取所有支持的策略
     */
    public List<String> getSupportedStrategies() {
        return List.copyOf(engineMap.keySet());
    }
}
