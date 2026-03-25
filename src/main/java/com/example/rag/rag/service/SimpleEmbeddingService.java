package com.example.rag.rag.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.rag.common.config.RagProperties;
import com.example.rag.rag.dto.OllamaEmbeddingRequest;
import com.example.rag.rag.dto.OllamaEmbeddingResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SimpleEmbeddingService implements EmbeddingService {

    private final RestClient restClient;
    private final RagProperties ragProperties;

    public SimpleEmbeddingService(RestClient restClient, RagProperties ragProperties) {
        this.restClient = restClient;
        this.ragProperties = ragProperties;
    }

    @Override
    public double[] embed(String text) {
        String apiUrl = ragProperties.getEmbedding().getApiUrl();
        if (!StringUtils.hasText(apiUrl)) {
            throw new IllegalStateException("임베딩 API URL이 설정되지 않았습니다.");
        }

        // null 입력은 빈 문자열로 치환해 외부 API 요청 형식을 안정적으로 맞춘다.
        String prompt = text == null ? "" : text;
        String configuredModel = ragProperties.getEmbedding().getModel();
        // 설정값에 태그가 없으면 :latest 를 붙여 Ollama 모델명을 표준화한다.
        String primaryModel = normalizeModelName(configuredModel);
        // 설정이 nomic-embed-text 처럼 태그 없이 들어온 경우만 fallback 후보를 만든다.
        String fallbackModel = fallbackModelName(configuredModel, primaryModel);

        OllamaEmbeddingResponse response;
        try {
            response = requestEmbedding(apiUrl, primaryModel, prompt);
        } catch (HttpClientErrorException.NotFound e) {
            // 404는 대개 모델명이 없거나 pull 되지 않은 경우라서 1회 fallback 모델명으로 재시도한다.
            if (fallbackModel == null) {
                throw new IllegalStateException("Ollama 임베딩 모델을 찾을 수 없습니다: " + primaryModel, e);
            }
            try {
                response = requestEmbedding(apiUrl, fallbackModel, prompt);
            } catch (RestClientException retryException) {
                throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", retryException);
            } catch (JsonMappingException JsonMappingException) {
                throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", JsonMappingException);
            } catch (JsonProcessingException JsonProcessingException) {
                throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", JsonProcessingException);
            }
        } catch (RestClientException RestClientException) {
            throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", RestClientException);
        } catch (JsonMappingException JsonMappingException) {
            throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", JsonMappingException);
        } catch (JsonProcessingException JsonProcessingException) {
            throw new IllegalStateException("Ollama 임베딩 API 호출에 실패했습니다.", JsonProcessingException);
        }

        if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
            throw new IllegalStateException("Ollama 임베딩 응답에 embedding 값이 없습니다.");
        }

        return toArray(response.embedding());
    }

    @Override
    public String modelName() {
        return normalizeModelName(ragProperties.getEmbedding().getModel());
    }

    // Ollama embeddings API 는 배열 대신 단일 embedding 필드를 반환하므로 문자열 응답을 직접 파싱한다.
    private OllamaEmbeddingResponse requestEmbedding(String apiUrl, String model, String prompt) throws RestClientException, JsonMappingException, JsonProcessingException {
        String normalizedApiUrl = apiUrl == null ? "" : apiUrl.trim();
        String normalizedModel = model == null ? "nomic-embed-text" : model.trim();

        Map<String, Object> body = new HashMap<>();
        body.put("model", normalizedModel);
        body.put("prompt", prompt);

        String rawResponse = restClient.post()
                .uri(normalizedApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return new ObjectMapper().readValue(rawResponse, OllamaEmbeddingResponse.class);
    }

    // 설정값이 비어 있거나 태그가 없더라도 항상 model:tag 형태를 반환한다.
    private String normalizeModelName(String configuredModel) {
        String value = StringUtils.hasText(configuredModel)
                ? configuredModel.trim()
                : "nomic-embed-text:latest";
        return value.contains(":") ? value : value + ":latest";
    }

    // 사용자가 태그 없는 모델명을 입력했을 때만 latest 태그 버전을 fallback 으로 둔다.
    private String fallbackModelName(String configuredModel, String primaryModel) {
        if (!StringUtils.hasText(configuredModel)) {
            return null;
        }
        String trimmed = configuredModel.trim();
        if (trimmed.contains(":")) {
            return null;
        }
        String fallback = trimmed + ":latest";
        return fallback.equals(primaryModel) ? null : fallback;
    }

    // null 값이 들어와도 벡터 길이를 유지하기 위해 0.0 으로 치환한다.
    private double[] toArray(List<Double> values) {
        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            vector[i] = value == null ? 0.0d : value;
        }
        return vector;
    }
}