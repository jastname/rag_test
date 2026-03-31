package com.example.rag.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimpleEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SimpleEmbeddingService.class);
    private final EmbeddingModel embeddingModel;
    private final String embeddingModelName;

    public SimpleEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text:latest}") String embeddingModelName) {
        this.embeddingModel = embeddingModel;
        this.embeddingModelName = embeddingModelName;
    }

    @Override
    public double[] embed(String text) {
        // null 입력은 빈 문자열로 치환해 API 요청 형식을 안정적으로 맞춘다.
        String prompt = text == null ? "" : text;

        EmbeddingResponse response;
        try {
            response = embeddingModel.embedForResponse(java.util.List.of(prompt));
        } catch (Exception e) {
            throw new IllegalStateException("Spring AI EmbeddingModel 호출에 실패했습니다.", e);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("임베딩 응답에 embedding 값이 없습니다.");
        }

        float[] floatVector = response.getResults().get(0).getOutput();
        return toDoubleArray(floatVector);
    }

    @Override
    public String modelName() {
        return embeddingModelName;
    }

    private double[] toDoubleArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}