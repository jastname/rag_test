package com.example.rag.rag.service;

import java.util.List;
import java.util.StringJoiner;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.rag.common.config.RagProperties;
import com.example.rag.rag.dto.OllamaGenerateRequest;
import com.example.rag.rag.dto.OllamaGenerateResponse;
import com.example.rag.rag.dto.RagChunkResult;

@Service
public class AnswerGenerationService {

    private final RestClient restClient;
    private final RagProperties ragProperties;

    public AnswerGenerationService(RestClient restClient, RagProperties ragProperties) {
        this.restClient = restClient;
        this.ragProperties = ragProperties;
    }

    public String generateAnswer(String question, List<RagChunkResult> references) {
        if (!StringUtils.hasText(question)) {
            return "질문이 비어 있습니다. 질문 내용을 입력해주세요.";
        }
        if (references == null || references.isEmpty()) {
            return "관련 청크를 찾지 못했습니다. 먼저 RAG 인덱싱을 실행한 뒤 다시 질문해주세요.";
        }

        String apiUrl = ragProperties.getLlm().getApiUrl();
        if (!StringUtils.hasText(apiUrl)) {
            throw new IllegalStateException("LLM API URL이 설정되지 않았습니다.");
        }

        String prompt = buildPrompt(question, references);
        OllamaGenerateResponse response;
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);

            String apiKey = ragProperties.getLlm().getApiKey();
            // 로컬 Ollama 는 보통 키가 없지만, 외부 호환 API 로 바꿔도 같은 코드를 재사용할 수 있게 열어둔다.
            if (StringUtils.hasText(apiKey)) {
                request = request.header("Authorization", "Bearer " + apiKey);
            }

            response = request
                    .body(new OllamaGenerateRequest(modelName(), prompt, false))
                    .retrieve()
                    .body(OllamaGenerateResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama 생성 API 호출에 실패했습니다.", e);
        }

        if (response == null || !StringUtils.hasText(response.response())) {
            throw new IllegalStateException("Ollama 생성 응답에 response 값이 없습니다.");
        }

        return response.response().trim();
    }

    private String modelName() {
        String configuredModel = ragProperties.getLlm().getModel();
        return StringUtils.hasText(configuredModel)
                ? configuredModel
                : "qwen3:4b";
    }

    private String buildPrompt(String question, List<RagChunkResult> references) {
        StringJoiner joiner = new StringJoiner("\n\n");
        for (int i = 0; i < references.size(); i++) {
            RagChunkResult ref = references.get(i);
            // 모델이 참조 순서를 이해하기 쉽도록 번호와 제목을 함께 묶어 전달한다.
            joiner.add("#" + (i + 1) + " " + safe(ref.getTitle()) + "\n"
                    + "<pre>" + extractDescription(ref.getChunkText()) + "</pre>");
        }

        return "당신은 검색 증강 생성(RAG) 도우미입니다. "
                + "반드시 제공된 문맥만 근거로 한국어로 답변하세요. "
                + "어린아이에게 알려주는 식으로 답변하세요. "
                + "문맥에 없는 내용은 추측하지 말고 모른다고 답하세요. \n\n"
                + "질문:\n" + question + "\n\n"
                + "검색 문맥:\n" + joiner;
    }

    private String extractDescription(String chunkText) {
        if (!StringUtils.hasText(chunkText)) {
            return "";
        }
        for (String line : chunkText.split("\\n")) {
            if (line.startsWith("description: ")) {
                return line.substring("description: ".length()).trim();
            }
        }
        return chunkText.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}