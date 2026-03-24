package com.example.rag.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.collect.kcisa")
public class KcisaProperties {

    private String baseUrl;
    private String endpoint = "/openapi/service/rest/meta14/getNLCF031801";
    private String serviceKey;
    private int numOfRows;
    private String responseType = "json";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public int getNumOfRows() {
        return numOfRows;
    }

    public void setNumOfRows(int numOfRows) {
        this.numOfRows = numOfRows;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getNormalizedBaseUrl() {
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getNormalizedEndpoint() {
        if (endpoint == null || endpoint.isBlank()) {
            return "/openapi/service/rest/meta14/getNLCF031801";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }
}