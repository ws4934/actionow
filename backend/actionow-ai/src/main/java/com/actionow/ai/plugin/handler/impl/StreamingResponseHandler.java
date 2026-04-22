package com.actionow.ai.plugin.handler.impl;

import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式响应处理器
 * 处理SSE流式响应
 *
 * @author Actionow
 */
@Slf4j
public class StreamingResponseHandler implements ResponseHandler, ResponseHandler.StreamingResponseHandler {

    private final ObjectMapper objectMapper;
    private final BlockingResponseHandler blockingHandler;

    public StreamingResponseHandler() {
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.blockingHandler = new BlockingResponseHandler();
    }

    @Override
    public ResponseMode getResponseMode() {
        return ResponseMode.STREAMING;
    }

    @Override
    public PluginExecutionResult handleResponse(Object rawResponse, PluginConfig config) {
        // 流式模式不直接处理完整响应
        return blockingHandler.handleResponse(rawResponse, config);
    }

    @Override
    public Map<String, Object> applyResponseMapping(Object rawResponse, Map<String, Object> responseMapping) {
        return blockingHandler.applyResponseMapping(rawResponse, responseMapping);
    }

    @Override
    public Flux<PluginStreamEvent> handleStreamResponse(Flux<String> eventFlux, PluginConfig config) {
        AtomicReference<String> textAccumulated = new AtomicReference<>("");
        AtomicReference<String> externalTaskId = new AtomicReference<>();

        return eventFlux
            .filter(line -> line != null && !line.isEmpty())
            .map(line -> parseStreamEvent(line, config, textAccumulated, externalTaskId))
            .filter(event -> event != null && !event.isPing());
    }

    /**
     * 解析SSE事件
     */
    @SuppressWarnings("unchecked")
    private PluginStreamEvent parseStreamEvent(String line, PluginConfig config,
                                               AtomicReference<String> textAccumulated,
                                               AtomicReference<String> externalTaskId) {
        // 处理SSE格式
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        } else if (line.startsWith("data: ")) {
            data = line.substring(6).trim();
        }

        if (data.isEmpty() || data.equals("[DONE]")) {
            return null;
        }

        try {
            Map<String, Object> eventData = objectMapper.readValue(data, Map.class);

            // 提取事件类型
            String eventType = (String) eventData.get("event");
            if (eventType == null) {
                eventType = (String) eventData.get("type");
            }

            // 提取任务ID
            String taskId = (String) eventData.get("task_id");
            if (taskId == null) {
                taskId = (String) eventData.get("taskId");
            }
            if (taskId != null) {
                externalTaskId.set(taskId);
            }

            // 根据事件类型处理
            return switch (normalizeEventType(eventType)) {
                case "workflow_started", "started" -> PluginStreamEvent.started(null, externalTaskId.get());

                case "node_started" -> PluginStreamEvent.builder()
                    .eventType(PluginStreamEvent.EventType.NODE_STARTED)
                    .externalTaskId(externalTaskId.get())
                    .currentStep((String) eventData.get("node_id"))
                    .data(eventData)
                    .build();

                case "node_finished" -> PluginStreamEvent.builder()
                    .eventType(PluginStreamEvent.EventType.NODE_FINISHED)
                    .externalTaskId(externalTaskId.get())
                    .currentStep((String) eventData.get("node_id"))
                    .data(eventData)
                    .build();

                case "text_chunk", "message" -> {
                    String delta = extractTextDelta(eventData);
                    if (delta != null) {
                        String accumulated = textAccumulated.updateAndGet(s -> s + delta);
                        yield PluginStreamEvent.textChunk(null, delta, accumulated);
                    }
                    yield null;
                }

                case "workflow_finished", "finished" -> {
                    Map<String, Object> outputs = extractOutputs(eventData);
                    yield PluginStreamEvent.finished(null, outputs);
                }

                case "error" -> {
                    String errorCode = (String) eventData.get("code");
                    String errorMessage = (String) eventData.get("message");
                    yield PluginStreamEvent.error(null, errorCode, errorMessage);
                }

                case "ping" -> PluginStreamEvent.builder()
                    .eventType(PluginStreamEvent.EventType.PING)
                    .build();

                default -> PluginStreamEvent.builder()
                    .eventType(PluginStreamEvent.EventType.UNKNOWN)
                    .data(eventData)
                    .rawEvent(data)
                    .build();
            };

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stream event: {}, data={}", e.getMessage(), data);
            return null;
        }
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null) return "unknown";
        return eventType.toLowerCase().replace("-", "_");
    }

    @SuppressWarnings("unchecked")
    private String extractTextDelta(Map<String, Object> eventData) {
        // 嵌套data格式
        Object data = eventData.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object text = dataMap.get("text");
            if (text instanceof String) {
                return (String) text;
            }
        }

        // OpenAI格式
        Object choices = eventData.get("choices");
        if (choices instanceof java.util.List<?> choiceList && !choiceList.isEmpty()) {
            Object choice = choiceList.get(0);
            if (choice instanceof Map<?, ?> choiceMap) {
                Object delta = choiceMap.get("delta");
                if (delta instanceof Map<?, ?> deltaMap) {
                    Object content = deltaMap.get("content");
                    if (content instanceof String) {
                        return (String) content;
                    }
                }
            }
        }

        // 直接文本字段
        Object text = eventData.get("text");
        if (text instanceof String) {
            return (String) text;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOutputs(Map<String, Object> eventData) {
        Map<String, Object> outputs = new HashMap<>();

        // 嵌套data格式
        Object data = eventData.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object outputsData = dataMap.get("outputs");
            if (outputsData instanceof Map) {
                outputs.putAll((Map<String, Object>) outputsData);
            }
        }

        // 直接outputs字段
        Object outputsField = eventData.get("outputs");
        if (outputsField instanceof Map) {
            outputs.putAll((Map<String, Object>) outputsField);
        }

        // 添加原始数据
        if (outputs.isEmpty()) {
            outputs.put("raw", eventData);
        }

        return outputs;
    }
}
