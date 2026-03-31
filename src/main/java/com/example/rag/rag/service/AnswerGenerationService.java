package com.example.rag.rag.service;

import java.util.List;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.rag.rag.dto.RagChunkResult;

import reactor.core.publisher.Flux;

@Service
public class AnswerGenerationService {

    // 검색 결과를 바탕으로 LLM 프롬프트를 만들고 실제 생성 API를 호출하는 서비스다.
    // 검색은 RagService가 담당하고, 여기서는 '어떻게 질문/문맥을 모델에 전달할지'에 집중한다.

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationService.class);
    private final ChatModel chatModel;

    private static final String USED_STORY_IDS_PREFIX = "[[USED_STORY_IDS:";
    private static final String USED_STORY_IDS_SUFFIX = "]]";

    public AnswerGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generateAnswer(String question, List<RagChunkResult> references) {
        // ...existing code...
        if (!StringUtils.hasText(question)) {
            return "질문이 비어 있습니다. 질문 내용을 입력해주세요.";
        }
        if (references == null || references.isEmpty()) {
            return "관련 청크를 찾지 못했습니다. 먼저 RAG 인덱싱을 실행한 뒤 다시 질문해주세요.";
        }

        log.info("[RAG][PROMPT] question='{}', referenceCount={}", question, references.size());

        String promptText = buildPrompt(question, references);
        log.info("[RAG][PROMPT] final prompt:\n{}", promptText);

        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(promptText));
        } catch (Exception e) {
            throw new IllegalStateException("Spring AI ChatModel 호출에 실패했습니다.", e);
        }

        if (response == null || response.getResult() == null
                || !StringUtils.hasText(response.getResult().getOutput().getText())) {
            throw new IllegalStateException("ChatModel 응답에 텍스트 값이 없습니다.");
        }

        String trimmedResponse = response
                                .getResult()
                                .getOutput()
                                .getText()
                                .trim();
        log.info("[RAG][PROMPT] model response:\n{}", trimmedResponse);
        return trimmedResponse;
    }

    /**
     * 스트리밍 방식으로 LLM 답변을 생성한다.
     * SseEmitter에 토큰 단위로 전송하면서 전체 응답도 수집하여 반환한다.
     * 검색 단계(search)는 호출 전에 완료되어 있어야 한다.
     */
    public String generateAnswerStream(String question, List<RagChunkResult> references, SseEmitter emitter) {
        if (!StringUtils.hasText(question)) {
            String msg = "질문이 비어 있습니다. 질문 내용을 입력해주세요.";
            sendSseEvent(emitter, msg);
            return msg;
        }
        if (references == null || references.isEmpty()) {
            String msg = "관련 청크를 찾지 못했습니다. 먼저 RAG 인덱싱을 실행한 뒤 다시 질문해주세요.";
            sendSseEvent(emitter, msg);
            return msg;
        }

        log.info("[RAG][STREAM] question='{}', referenceCount={}", question, references.size());

        String promptText = buildPrompt(question, references);
        log.info("[RAG][STREAM] final prompt:\n{}", promptText);

        StringBuilder fullResponse = new StringBuilder();
        try {
            Flux<ChatResponse> stream = chatModel.stream(new Prompt(promptText));
            // blockLast()로 스트림을 구독하면서 토큰마다 SSE 전송한다.
            // emitter의 완료 처리는 컨트롤러에서 담당하므로 여기서는 토큰 전송만 한다.
            stream.doOnNext(chatResponse -> {
                if (chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null
                        && chatResponse.getResult().getOutput().getText() != null) {
                    String token = chatResponse.getResult().getOutput().getText();
                    fullResponse.append(token);
                    sendSseEvent(emitter, token);
                }
            })
            .doOnComplete(() -> {
                log.info("[RAG][STREAM] stream completed. fullResponse length={}", fullResponse.length());
            })
            .doOnError(error -> {
                log.error("[RAG][STREAM] stream error", error);
            })
            .blockLast();
        } catch (Exception e) {
            log.error("[RAG][STREAM] ChatModel 스트리밍 호출 실패", e);
            emitter.completeWithError(e);
            throw new IllegalStateException("Spring AI ChatModel 스트리밍 호출에 실패했습니다.", e);
        }

        String result = fullResponse.toString().trim();
        log.info("[RAG][STREAM] model response:\n{}", result);
        return result;
    }

    private void sendSseEvent(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception e) {
            log.warn("[RAG][SSE] 토큰 전송 실패 (클라이언트 연결 종료 가능)", e);
        }
    }

    private void completeSse(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("[RAG][SSE] emitter complete 실패", e);
        }
    }

    // ...existing buildPrompt, extractDescription, safeStoryId, safe methods...

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
                + "답변 본문 마지막에는 질문과 관련있고 당신이 실제로 사용한 storyId만 '" + USED_STORY_IDS_PREFIX + "id1,id2" + USED_STORY_IDS_SUFFIX + "' 형식으로 한 번만 덧붙이세요. "
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