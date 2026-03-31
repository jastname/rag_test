package com.example.rag.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int chunkSize = 400;
    private int chunkOverlap = 80;
    private int topK = 5;
    private Embedding embedding = new Embedding();

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

    // 임베딩 관련 설정 중 Spring AI가 관리하지 않는 항목만 남긴다.
    public static class Embedding {
        private int dimensions = 64;
        // 제목 임베딩 벡터의 가중치. 나머지(1 - titleWeight)가 설명 벡터 가중치가 된다.
        private double titleWeight = 0.7;

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public double getTitleWeight() {
            return titleWeight;
        }

        public void setTitleWeight(double titleWeight) {
            this.titleWeight = titleWeight;
        }
    }
}