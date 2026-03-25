package com.example.rag.collect.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.rag.common.config.KcisaProperties;

@Component
public class KcisaApiClient {

    private final RestClient restClient;
    private final KcisaProperties kcisaProperties;

    public KcisaApiClient(RestClient restClient, KcisaProperties kcisaProperties) {
        this.restClient = restClient;
        this.kcisaProperties = kcisaProperties;
    }

    // 상위 서비스에서 직접 JSON 파싱과 이력 저장을 할 수 있도록 원본 문자열 그대로 반환한다.
    public String getStoriesRaw(int pageNo) {
        String url = buildUrl(pageNo);

        return restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    // 환경설정에 선언된 baseUrl, endpoint, serviceKey, page 정보를 조합해 최종 요청 URL을 만든다.
    public String buildUrl(int pageNo) {
        return UriComponentsBuilder
                .fromHttpUrl(kcisaProperties.getBaseUrl())
                .path(kcisaProperties.getEndpoint())
                .queryParam("serviceKey", kcisaProperties.getServiceKey())
                .queryParam("numOfRows", kcisaProperties.getNumOfRows())
                .queryParam("pageNo", pageNo)
                .toUriString();
    }

    public int getNumOfRows() {
        return kcisaProperties.getNumOfRows();
    }
}