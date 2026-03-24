package com.example.rag.rag.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}