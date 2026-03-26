package com.example.rag.rag.service;

import java.util.List;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // 검색 결과를 바탕으로 LLM 프롬프트를 만들고 실제 생성 API를 호출하는 서비스다.
    // 검색은 RagService가 담당하고, 여기서는 '어떻게 질문/문맥을 모델에 전달할지'에 집중한다.

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationService.class);
    private final RestClient restClient;
    private final RagProperties ragProperties;

    private static final String USED_STORY_IDS_PREFIX = "[[USED_STORY_IDS:";
    private static final String USED_STORY_IDS_SUFFIX = "]]";

    public AnswerGenerationService(RestClient restClient, RagProperties ragProperties) {
        this.restClient = restClient;
        this.ragProperties = ragProperties;
    }

    public String generateAnswer(String question, List<RagChunkResult> references) {
        // 빈 질문, 빈 검색 결과는 모델 호출 전에 빠르게 차단한다.
        // 이렇게 해야 불필요한 외부 호출과 애매한 모델 응답을 줄일 수 있다.
        if (!StringUtils.hasText(question)) {
            return "질문이 비어 있습니다. 질문 내용을 입력해주세요.";
        }
        if (references == null || references.isEmpty()) {
            return "관련 청크를 찾지 못했습니다. 먼저 RAG 인덱싱을 실행한 뒤 다시 질문해주세요.";
        }

        log.info("[RAG][PROMPT] question='{}', referenceCount={}", question, references.size());

        String apiUrl = ragProperties.getLlm().getApiUrl();
        if (!StringUtils.hasText(apiUrl)) {
            throw new IllegalStateException("LLM API URL이 설정되지 않았습니다.");
        }

        // 질문과 검색 문맥을 하나의 프롬프트로 합쳐 모델에 전달한다.
        // 프롬프트 전문은 디버깅을 위해 로그에 남긴다.
        String prompt = buildPrompt(question, references);
        log.info("[RAG][PROMPT] final prompt:\n{}", prompt);
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

            // stream=false 로 한 번에 완성된 응답을 받아 후처리를 단순화한다.
            response = request
                    .body(new OllamaGenerateRequest(modelName(), prompt, false))
                    .retrieve()
                    .body(OllamaGenerateResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama 생성 API 호출에 실패했습니다.", e);
        }

        // response 필드는 실제 모델 생성 텍스트이므로 비어 있으면 실패로 본다.
        if (response == null || !StringUtils.hasText(response.response())) {
            throw new IllegalStateException("Ollama 생성 응답에 response 값이 없습니다.");
        }

        String trimmedResponse = response.response().trim();
        log.info("[RAG][PROMPT] model response:\n{}", trimmedResponse);
        return trimmedResponse;
    }

    private String modelName() {
        // 모델명을 명시적으로 설정하지 않으면 기본 모델을 사용한다.
        // 운영 환경별 설정 파일에서 바꾸기 쉽게 메서드로 분리했다.
        String configuredModel = ragProperties.getLlm().getModel();
        return StringUtils.hasText(configuredModel)
                ? configuredModel
                : "qwen3:4b";
    }

    private String buildPrompt(String question, List<RagChunkResult> references) {
        // 검색 문맥은 번호 + storyId + 제목 + 설명 본문 형태로 직렬화한다.
        // 제목과 storyId를 함께 주는 이유는 모델이 어떤 근거를 사용했는지 추적 가능하게 만들기 위해서다.
        StringJoiner joiner = new StringJoiner("\n\n");
        for (int i = 0; i < references.size(); i++) {
            RagChunkResult ref = references.get(i);
            // 모델이 참조 순서를 이해하기 쉽도록 번호와 제목, storyId를 함께 묶어 전달한다.
            joiner.add("#" + (i + 1)
                    + " [storyId=" + safeStoryId(ref.getStoryId()) + "] " + safe(ref.getTitle()) + "\n"
                    + "<pre>" + extractDescription(ref.getChunkText()) + "</pre>");
        }

        // 시스템 지시문은 '문맥 밖 추측 금지'와 'USED_STORY_IDS 포맷 강제'가 핵심이다.
        // 후속 단계에서 storyId 사용 여부를 파싱하기 때문에 이 포맷은 중요하다.
        return "당신은 검색 증강 생성(RAG) 도우미입니다. "
                + "반드시 제공된 문맥만 근거로 한국어로 답변하세요. "
                + "어린아이에게 알려주는 식으로 답변하세요. "
                + "문맥에 없는 내용은 추측하지 말고 모른다고 답하세요. "
                + "답변 본문 마지막에는 당신이 실제로 사용한 storyId만 '" + USED_STORY_IDS_PREFIX + "id1,id2" + USED_STORY_IDS_SUFFIX + "' 형식으로 한 번만 덧붙이세요. "
                + "storyId를 전혀 사용하지 않았다면 '" + USED_STORY_IDS_PREFIX + USED_STORY_IDS_SUFFIX + "' 로 마무리하세요. "
                + "구분자 블록 밖에는 storyId 목록 설명을 쓰지 마세요.\n\n"
                + "질문:\n" + question + "\n\n"
                + "검색 문맥:\n" + joiner;
    }

    private String extractDescription(String chunkText) {
        // 프롬프트에는 구조화 마커 전체보다 description 본문만 넣는 편이 모델 품질에 유리하다.
        // description 라인이 없으면 원문 전체를 fallback 으로 사용한다.
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

    private String safeStoryId(Long storyId) {
        // storyId는 프롬프트 직렬화용 문자열 변환 유틸이다.
        // null 을 빈 문자열로 처리해 포맷 붕괴를 막는다.
        return storyId == null ? "" : String.valueOf(storyId);
    }

    private String safe(String value) {
        // null-safe 문자열 유틸. 프롬프트 조합 시 NPE를 방지한다.
        return value == null ? "" : value;
    }
}