package com.actionow.agent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini Embedding 适配器
 *
 * <p>将自定义 Gemini REST API 调用适配为 Spring AI {@link EmbeddingModel} 接口，
 * 供外部配置的 {@code VectorStore} bean（如 PgVectorStore）使用。
 *
 * <p>仅在 {@code actionow.saa.rag-enabled=true} 时激活。
 *
 * @author Actionow
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "actionow.saa", name = "rag-enabled", havingValue = "true")
@RequiredArgsConstructor
public class GeminiEmbeddingModelAdapter implements EmbeddingModel {

    private final EmbeddingConfig config;
    private final RestClient.Builder restClientBuilder;

    private RestClient restClient;
    private Cache<String, float[]> embeddingCache;

    private static final int CACHE_MAX_SIZE = 5000;
    private static final int CACHE_EXPIRE_MINUTES = 30;

    // ---- Gemini REST API DTOs ----
    private record Part(String text) {}
    private record Content(List<Part> parts) {}
    private record EmbedRequest(String model, Content content, String taskType, Integer outputDimensionality) {}
    private record EmbedValues(List<Double> values) {}
    private record EmbedContentResponse(EmbedValues embedding) {}
    private record BatchEmbedRequest(List<EmbedRequest> requests) {}
    private record BatchEmbedResponse(List<EmbedValues> embeddings) {}

    @PostConstruct
    public void init() {
        this.embeddingCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(CACHE_EXPIRE_MINUTES))
                .build();

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            this.restClient = restClientBuilder.baseUrl(config.getBaseUrl()).build();
            log.info("GeminiEmbeddingModelAdapter initialized: model={}, dimension={}",
                    config.getModel(), config.getDimension());
        } else {
            log.warn("GeminiEmbeddingModelAdapter: GOOGLE_API_KEY not configured, RAG will return empty results");
        }
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        if (inputs.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }

        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        if (inputs.size() == 1) {
            float[] vector = embedSingle(inputs.get(0));
            embeddings.add(new Embedding(vector, 0));
        } else {
            List<float[]> vectors = embedBatch(inputs);
            for (int i = 0; i < vectors.size(); i++) {
                embeddings.add(new Embedding(vectors.get(i), i));
            }
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedSingle(document.getFormattedContent());
    }

    @Override
    public int dimensions() {
        return config.getDimension();
    }

    private float[] embedSingle(String text) {
        if (restClient == null) {
            return new float[config.getDimension()];
        }
        float[] cached = embeddingCache.getIfPresent(text);
        if (cached != null) return cached;

        try {
            String modelPath = "models/" + config.getModel();
            EmbedRequest body = new EmbedRequest(
                    modelPath,
                    new Content(List.of(new Part(text))),
                    "RETRIEVAL_QUERY",
                    config.getDimension()
            );
            EmbedContentResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:embedContent?key={key}", config.getModel(), config.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(EmbedContentResponse.class);

            if (response != null && response.embedding() != null && response.embedding().values() != null) {
                float[] result = toFloatArray(response.embedding().values());
                embeddingCache.put(text, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("GeminiEmbeddingModelAdapter: embed failed: {}", e.getMessage());
        }
        return new float[config.getDimension()];
    }

    private List<float[]> embedBatch(List<String> texts) {
        if (restClient == null) {
            return texts.stream().map(t -> new float[config.getDimension()]).toList();
        }

        float[][] results = new float[texts.size()][];
        List<String> uncached = new ArrayList<>();
        List<Integer> uncachedIdx = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            float[] c = embeddingCache.getIfPresent(texts.get(i));
            if (c != null) {
                results[i] = c;
            } else {
                uncached.add(texts.get(i));
                uncachedIdx.add(i);
            }
        }

        if (!uncached.isEmpty()) {
            try {
                String modelPath = "models/" + config.getModel();
                List<EmbedRequest> requests = uncached.stream()
                        .map(t -> new EmbedRequest(modelPath,
                                new Content(List.of(new Part(t))),
                                "RETRIEVAL_QUERY",
                                config.getDimension()))
                        .toList();
                BatchEmbedResponse response = restClient.post()
                        .uri("/v1beta/models/{model}:batchEmbedContents?key={key}", config.getModel(), config.getApiKey())
                        .body(new BatchEmbedRequest(requests))
                        .retrieve()
                        .body(BatchEmbedResponse.class);

                if (response != null && response.embeddings() != null) {
                    for (int i = 0; i < response.embeddings().size() && i < uncachedIdx.size(); i++) {
                        EmbedValues ev = response.embeddings().get(i);
                        int idx = uncachedIdx.get(i);
                        if (ev != null && ev.values() != null) {
                            float[] arr = toFloatArray(ev.values());
                            results[idx] = arr;
                            embeddingCache.put(uncached.get(i), arr);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("GeminiEmbeddingModelAdapter: batch embed failed: {}", e.getMessage());
            }
        }

        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) results[i] = new float[config.getDimension()];
        }
        return List.of(results);
    }

    private float[] toFloatArray(List<Double> values) {
        float[] arr = new float[values.size()];
        for (int i = 0; i < values.size(); i++) arr[i] = values.get(i).floatValue();
        return arr;
    }
}
