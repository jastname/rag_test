package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.example.rag.common.config.RagProperties;
import com.example.rag.rag.dto.RagChunkResult;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class AnswerGenerationServiceTest {

    private final MockWebServer server = new MockWebServer();

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void generateAnswerCallsOllamaGenerateApi() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{" +
                        "\"response\":\"요약 답변입니다.\"," +
                        "\"done\":true" +
                        "}"));

        RagProperties properties = new RagProperties();
        properties.getLlm().setApiUrl(server.url("/api/generate").toString());
        properties.getLlm().setModel("qwen3:4b");

        AnswerGenerationService service = new AnswerGenerationService(RestClient.create(), properties);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setTitle("설화 제목");
        chunk.setChunkText("옛날 옛적 이야기입니다.");
        chunk.setSourceUrl("https://example.com/story");
        chunk.setSimilarity(0.91d);

        String answer = service.generateAnswer("무슨 이야기야?", List.of(chunk));
        RecordedRequest request = server.takeRequest();

        assertThat(answer).isEqualTo("요약 답변입니다.");
        assertThat(request.getPath()).isEqualTo("/api/generate");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"qwen3:4b\"")
                .contains("\"stream\":false")
                .contains("무슨 이야기야?")
                .contains("설화 제목");
    }

    @Test
    void generateAnswerFailsWhenResponseIsBlank() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{" +
                        "\"response\":\"\"," +
                        "\"done\":true" +
                        "}"));

        RagProperties properties = new RagProperties();
        properties.getLlm().setApiUrl(server.url("/api/generate").toString());

        AnswerGenerationService service = new AnswerGenerationService(RestClient.create(), properties);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkText("문맥");

        assertThatThrownBy(() -> service.generateAnswer("질문", List.of(chunk)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response 값이 없습니다");
    }
}
