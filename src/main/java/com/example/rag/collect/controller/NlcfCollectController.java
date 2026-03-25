package com.example.rag.collect.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.rag.collect.service.NlcfCollectService;

@RestController
public class NlcfCollectController {

    private final NlcfCollectService nlcfCollectService;

    public NlcfCollectController(NlcfCollectService nlcfCollectService) {
        this.nlcfCollectService = nlcfCollectService;
    }

    // 지정한 페이지 번호에 대해 현재 수집 가능한 건수를 확인한다.
    @RequestMapping("/api/collect/nlcf/{pageNo}")
    public Map<String, Object> collectPage(@PathVariable("pageNo") int pageNo) {
        int count = nlcfCollectService.collectPage(pageNo);

        return Map.of(
                "success", true,
                "pageNo", pageNo,
                "savedCount", count
        );
    }

    // 전체 페이지를 1페이지부터 순차 수집한다.
    // 내부적으로 각 페이지 응답 이력과 스토리 데이터를 DB에 저장하며,
    // 더 이상 수집할 데이터가 없으면 반복을 종료한다.
    @RequestMapping("/api/collect/nlcf/all")
    public Map<String, Object> collectAll() {
        int totalCount = nlcfCollectService.collectAll();

        return Map.of(
                "success", true,
                // 실제 DB에 적재된 전체 건수를 반환한다.
                "savedCount", totalCount
        );
    }
}