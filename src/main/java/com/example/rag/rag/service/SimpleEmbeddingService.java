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

        String prompt = text == null ? "" : text;
        String configuredModel = ragProperties.getEmbedding().getModel();
        String primaryModel = normalizeModelName(configuredModel);
        String fallbackModel = fallbackModelName(configuredModel, primaryModel);
        
        System.out.println("#####################################");
        System.out.println(primaryModel);
        System.out.println(fallbackModel);
        System.out.println("#####################################");
        
        OllamaEmbeddingResponse response;
        try {
            response = requestEmbedding(apiUrl, primaryModel, prompt);
        } catch (HttpClientErrorException.NotFound e) {
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

    private OllamaEmbeddingResponse requestEmbedding(String apiUrl, String model, String prompt) throws RestClientException, JsonMappingException, JsonProcessingException{
        String normalizedApiUrl = apiUrl == null ? "" : apiUrl.trim();
        String normalizedModel = model == null ? "nomic-embed-text" : model.trim();

        Map<String, Object> body = new HashMap<>();
        body.put("model", normalizedModel);
        body.put("prompt", prompt);

		/*
		 System.out.println("#######API URL########[" + normalizedApiUrl + "]");
		 System.out.println("#######Model########[" + normalizedModel + "]");
		 System.out.println("#######Body########" + body);
		 */
        
        String rawResponse = restClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        System.out.println("#######RAW RESPONSE########" + rawResponse);
        
        return new ObjectMapper().readValue(rawResponse, OllamaEmbeddingResponse.class);
    }

    private String normalizeModelName(String configuredModel) {
        String value = StringUtils.hasText(configuredModel)
                ? configuredModel.trim()
                : "nomic-embed-text:latest";
        return value.contains(":") ? value : value + ":latest";
    }

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

    private double[] toArray(List<Double> values) {
        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            vector[i] = value == null ? 0.0d : value;
        }
        return vector;
    }
}