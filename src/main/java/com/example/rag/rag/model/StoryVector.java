package com.example.rag.rag.model;

public class StoryVector {

    private Long embeddingId;
    private Long chunkId;
    private Long storyId;
    private String embeddingModel;
    private String vectorJson;

    public Long getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(Long embeddingId) {
        this.embeddingId = embeddingId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getStoryId() {
        return storyId;
    }

    public void setStoryId(Long storyId) {
        this.storyId = storyId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getVectorJson() {
        return vectorJson;
    }

    public void setVectorJson(String vectorJson) {
        this.vectorJson = vectorJson;
    }
}