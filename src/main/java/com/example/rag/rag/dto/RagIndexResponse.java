package com.example.rag.rag.dto;

public class RagIndexResponse {

    private boolean success;
    private String message;
    private int storyCount;
    private int chunkCount;
    private int vectorCount;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStoryCount() {
        return storyCount;
    }

    public void setStoryCount(int storyCount) {
        this.storyCount = storyCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getVectorCount() {
        return vectorCount;
    }

    public void setVectorCount(int vectorCount) {
        this.vectorCount = vectorCount;
    }
}