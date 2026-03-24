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

    public String getStoriesRaw(int pageNo) {
        String url = buildUrl(pageNo);

        return restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

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