package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

class SimpleEmbeddingServiceTest {

    @Test
    void embedReturnsVectorFromSpringAiModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedForResponse(any()))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{0.12f, 0.34f, 0.56f}, 0)), new EmbeddingResponseMetadata()));

        SimpleEmbeddingService service = new SimpleEmbeddingService(embeddingModel, "nomic-embed-text:latest");

        double[] vector = service.embed("테스트 문장");

        assertThat(vector).hasSize(3);
        assertThat(vector[0]).isCloseTo(0.12d, org.assertj.core.data.Offset.offset(0.001d));
        assertThat(vector[1]).isCloseTo(0.34d, org.assertj.core.data.Offset.offset(0.001d));
        assertThat(vector[2]).isCloseTo(0.56d, org.assertj.core.data.Offset.offset(0.001d));
    }

    @Test
    void embedFailsWhenResponseHasNoEmbedding() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedForResponse(any()))
                .thenReturn(new EmbeddingResponse(List.of(), new EmbeddingResponseMetadata()));

        SimpleEmbeddingService service = new SimpleEmbeddingService(embeddingModel, "nomic-embed-text:latest");

        assertThatThrownBy(() -> service.embed("질문"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding 값이 없습니다");
    }

    @Test
    void modelNameReturnsInjectedValue() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedForResponse(any()))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{0.21f, 0.43f}, 0)), new EmbeddingResponseMetadata()));

        SimpleEmbeddingService service = new SimpleEmbeddingService(embeddingModel, "nomic-embed-text:latest");

        double[] vector = service.embed("태그 없는 모델 테스트");

        assertThat(vector).hasSize(2);
        assertThat(vector[0]).isCloseTo(0.21d, org.assertj.core.data.Offset.offset(0.001d));
        assertThat(vector[1]).isCloseTo(0.43d, org.assertj.core.data.Offset.offset(0.001d));
        assertThat(service.modelName()).isEqualTo("nomic-embed-text:latest");
    }
}