package com.example.rag.rag.model;

public class StoryChunk {

    private Long chunkId;
    private Long storyId;
    private int chunkIndex;
    private String chunkText;
    private String vectorJson;

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

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public String getVectorJson() {
        return vectorJson;
    }

    public void setVectorJson(String vectorJson) {
        this.vectorJson = vectorJson;
    }
}