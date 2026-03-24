package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.example.rag.common.config.RagProperties;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class SimpleEmbeddingServiceTest {

    private final MockWebServer server = new MockWebServer();

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void embedCallsOllamaEmbeddingsApi() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{" +
                        "\"embedding\":[0.12,0.34,0.56]" +
                        "}"));

        RagProperties properties = new RagProperties();
        properties.getEmbedding().setApiUrl(server.url("/api/embeddings").toString());
        properties.getEmbedding().setModel("nomic-embed-text:latest");

        SimpleEmbeddingService service = new SimpleEmbeddingService(RestClient.create(), properties);

        double[] vector = service.embed("테스트 문장");
        RecordedRequest request = server.takeRequest();

        assertThat(vector).containsExactly(0.12d, 0.34d, 0.56d);
        assertThat(request.getPath()).isEqualTo("/api/embeddings");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"nomic-embed-text:latest\"")
                .contains("\"prompt\":\"테스트 문장\"");
    }

    @Test
    void embedFailsWhenResponseHasNoEmbedding() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        RagProperties properties = new RagProperties();
        properties.getEmbedding().setApiUrl(server.url("/api/embeddings").toString());

        SimpleEmbeddingService service = new SimpleEmbeddingService(RestClient.create(), properties);

        assertThatThrownBy(() -> service.embed("질문"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding 값이 없습니다");
    }

    @Test
    void embedNormalizesUntaggedModelNameToLatest() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{" +
                        "\"embedding\":[0.21,0.43]" +
                        "}"));

        RagProperties properties = new RagProperties();
        properties.getEmbedding().setApiUrl(server.url("/api/embeddings").toString());
        properties.getEmbedding().setModel("nomic-embed-text");

        SimpleEmbeddingService service = new SimpleEmbeddingService(RestClient.create(), properties);

        double[] vector = service.embed("태그 없는 모델 테스트");
        RecordedRequest request = server.takeRequest();

        assertThat(vector).containsExactly(0.21d, 0.43d);
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"nomic-embed-text:latest\"");
        assertThat(service.modelName()).isEqualTo("nomic-embed-text:latest");
    }
}