package com.example.rag.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int chunkSize = 400;
    private int chunkOverlap = 80;
    private int topK = 5;
    private Embedding embedding = new Embedding();
    private Llm llm = new Llm();

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    // 임베딩 모델 호출에 필요한 설정 묶음이다.
    public static class Embedding {
        private int dimensions = 64;
        private String model = "nomic-embed-text:latest";
        private String apiUrl;
        private String apiKey;

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    // 답변 생성용 LLM 호출에 필요한 설정 묶음이다.
    public static class Llm {
        private String apiUrl;
        private String apiKey;
        private String model = "local-rag-template";

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}