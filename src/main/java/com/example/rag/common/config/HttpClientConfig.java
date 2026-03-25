package com.example.rag.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        // 외부 수집 API와 Ollama 호출에 공통으로 사용하는 기본 HTTP 클라이언트다.
        return RestClient.builder().build();
    }
}