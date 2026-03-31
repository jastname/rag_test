package com.example.rag.rag.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.rag.rag.dto.RagAskRequest;
import com.example.rag.rag.dto.RagAskResponse;
import com.example.rag.rag.dto.RagIndexResponse;
import com.example.rag.rag.service.RagService;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    // story DB 전체를 조회해서 chunk/embedding을 생성하고 저장한다.
    @PostMapping("/embed/all-stories")
    public RagIndexResponse embedAllStories() {
        return ragService.embedAllStories();
    }

    // 2~4단계: story를 chunk로 분리하고 임베딩을 만든 뒤 DB에 다시 저장한다.
    @PostMapping("/index")
    public RagIndexResponse rebuildIndex() {
        return ragService.rebuildIndex();
    }

    // 5~6단계의 메인 엔드포인트: 질문 임베딩 -> top-k 청크 검색 -> 답변 생성.
    @PostMapping("/ask")
    public RagAskResponse ask(@Valid @RequestBody RagAskRequest request) {
        return ragService.ask(request.getQuestion(), request.getTopK());
    }

    /**
     * SSE 스트리밍 방식의 질문 엔드포인트.
     * 이벤트 전송 순서:
     *   1) "references" — 검색된 참조 청크 목록 (토큰 스트리밍 시작 전)
     *   2) "token" (반복) — LLM 토큰이 생성될 때마다 전송
     *   3) "result" — 스트리밍 완료 후 최종 RagAskResponse 전송
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody RagAskRequest request) {
        // 타임아웃 5분 (LLM 응답이 길 수 있으므로 넉넉하게 설정)
        SseEmitter emitter = new SseEmitter(300_000L);

        // 별도 스레드에서 검색+스트리밍을 수행해 Servlet 스레드를 반환한다.
        new Thread(() -> {
            try {
                RagAskResponse response = ragService.askStream(
                        request.getQuestion(), request.getTopK(), emitter);
                // 스트리밍 완료 후 최종 결과를 "result" 이벤트로 전송한다.
                emitter.send(SseEmitter.event().name("result").data(response));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}