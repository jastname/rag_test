package com.example.rag.collect.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.rag.collect.client.KcisaApiClient;
import com.example.rag.collect.dto.KcisaItem;
import com.example.rag.collect.dto.KcisaResponse;
import com.example.rag.collect.mapper.NlcfCollectMapper;
import com.example.rag.collect.model.CollectHistory;
import com.example.rag.common.util.HashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NlcfCollectService {

    private static final Logger log = LoggerFactory.getLogger(NlcfCollectService.class);
    private static final long PAGE_REQUEST_DELAY_MILLIS = 500L;

    private final KcisaApiClient kcisaApiClient;
    private final NlcfCollectMapper nlcfCollectMapper;
    private final ObjectMapper objectMapper;

    public NlcfCollectService(
            KcisaApiClient kcisaApiClient,
            NlcfCollectMapper nlcfCollectMapper,
            ObjectMapper objectMapper
    ) {
        this.kcisaApiClient = kcisaApiClient;
        this.nlcfCollectMapper = nlcfCollectMapper;
        this.objectMapper = objectMapper;
    }

    public int collectPage(int pageNo) {
        CollectPageResult collectPageResult = fetchPage(pageNo);
        return collectPageResult.getCollectedCount();
    }

    public int collectAndSavePage(int pageNo) {
        CollectPageResult collectPageResult = fetchPage(pageNo);
        List<KcisaItem> items = collectPageResult.getItems();

        CollectHistory collectHistory = new CollectHistory();
        collectHistory.setRequestUrl(collectPageResult.getRequestUrl());
        collectHistory.setPageNo(pageNo);
        collectHistory.setNumOfRows(kcisaApiClient.getNumOfRows());
        collectHistory.setResultCode(collectPageResult.getResultCode());
        collectHistory.setResultMsg(collectPageResult.getResultMsg());
        collectHistory.setSuccessYn(collectPageResult.getSuccessYn());
        collectHistory.setErrorMessage(collectPageResult.getErrorMessage());
        collectHistory.setCollectedCount(collectPageResult.getCollectedCount());
        collectHistory.setRawResponse(collectPageResult.getRawResponse());

        Long collectId = nlcfCollectMapper.insertCollectHistory(collectHistory);

        for (KcisaItem item : items) {
            item.setCollectId(collectId);
            item.setHashKey(makeHashKey(item));
            nlcfCollectMapper.upsertStory(item);
        }

        return collectPageResult.getCollectedCount();
    }

    public int collectAll() {
        int totalCount = 0;
        int pageNo = 1;

        log.info("[NLCF COLLECT] 전체 재수집 시작 - 기존 데이터를 삭제한 후 다시 적재합니다.");
        resetCollectedData();

        while (true) {
            int savedCount = collectAndSavePage(pageNo);

            if (savedCount <= 0) {
                log.info("[NLCF COLLECT] page={} 데이터가 없어 수집을 종료합니다. totalSavedCount={}", pageNo, totalCount);
                break;
            }

            totalCount += savedCount;
            log.info("[NLCF COLLECT] 진행중 page={} savedCount={} totalSavedCount={}", pageNo, savedCount, totalCount);
            pageNo++;
            waitBeforeNextPage(pageNo);
        }

        log.info("[NLCF COLLECT] 전체 재수집 완료 totalSavedCount={}", totalCount);
        return totalCount;
    }

    private CollectPageResult fetchPage(int pageNo) {
        String requestUrl = kcisaApiClient.buildUrl(pageNo);
        String rawResponse = kcisaApiClient.getStoriesRaw(pageNo);

        List<KcisaItem> items = Collections.emptyList();
        String resultCode = "9999";
        String resultMsg = "UNKNOWN";
        String successYn = "N";
        String errorMessage = null;

        try {
            KcisaResponse response = objectMapper.readValue(rawResponse, KcisaResponse.class);

            if (response != null && response.getResponse() != null) {
                if (response.getResponse().getHeader() != null) {
                    resultCode = nvl(response.getResponse().getHeader().getResultCode());
                    resultMsg = nvl(response.getResponse().getHeader().getResultMsg());
                }

                if (response.getResponse().getBody() != null
                        && response.getResponse().getBody().getItems() != null
                        && response.getResponse().getBody().getItems().getItem() != null) {
                    items = response.getResponse().getBody().getItems().getItem();
                }
            }

            if ("0000".equals(resultCode) || items != null) {
                successYn = "Y";
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        return new CollectPageResult(requestUrl, rawResponse, items, resultCode, resultMsg, successYn, errorMessage);
    }

    private String makeHashKey(KcisaItem item) {
        String base = String.join("|",
                nvl(item.getTitle()),
                nvl(item.getCreator()),
                nvl(item.getRegDate()),
                nvl(item.getUrl()));

        return HashUtil.sha256(base);
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }

    private void resetCollectedData() {
        nlcfCollectMapper.deleteAllStories();
        nlcfCollectMapper.deleteAllCollectHistory();
    }

    private void waitBeforeNextPage(int nextPageNo) {
        log.info("[NLCF COLLECT] 다음 페이지 요청 대기 nextPage={} delayMs={}", nextPageNo, PAGE_REQUEST_DELAY_MILLIS);
        try {
            TimeUnit.MILLISECONDS.sleep(PAGE_REQUEST_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("다음 페이지 요청 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    private static class CollectPageResult {
        private final String requestUrl;
        private final String rawResponse;
        private final List<KcisaItem> items;
        private final String resultCode;
        private final String resultMsg;
        private final String successYn;
        private final String errorMessage;

        private CollectPageResult(
                String requestUrl,
                String rawResponse,
                List<KcisaItem> items,
                String resultCode,
                String resultMsg,
                String successYn,
                String errorMessage
        ) {
            this.requestUrl = requestUrl;
            this.rawResponse = rawResponse;
            this.items = items == null ? Collections.emptyList() : items;
            this.resultCode = resultCode;
            this.resultMsg = resultMsg;
            this.successYn = successYn;
            this.errorMessage = errorMessage;
        }

        private String getRequestUrl() {
            return requestUrl;
        }

        private String getRawResponse() {
            return rawResponse;
        }

        private List<KcisaItem> getItems() {
            return items;
        }

        private String getResultCode() {
            return resultCode;
        }

        private String getResultMsg() {
            return resultMsg;
        }

        private String getSuccessYn() {
            return successYn;
        }

        private String getErrorMessage() {
            return errorMessage;
        }

        private int getCollectedCount() {
            return items.size();
        }
    }
}