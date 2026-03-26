package com.example.rag.rag.dto;

import java.util.List;

public class RagAskResponse {

    private boolean success;
    private String question;
    private String answer;
    private String rawAnswer;
    private String thinkAnswer;
    private String coreAnswer;
    private int topK;
    private int matchedChunkCount;
    private List<RagChunkResult> references;
    private List<Long> relatedStoryIds;
    private List<Long> usedStoryIds;
    private Long primaryStoryId;
    private RagChunkResult primaryReference;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getRawAnswer() {
        return rawAnswer;
    }

    public void setRawAnswer(String rawAnswer) {
        this.rawAnswer = rawAnswer;
    }

    public String getThinkAnswer() {
        return thinkAnswer;
    }

    public void setThinkAnswer(String thinkAnswer) {
        this.thinkAnswer = thinkAnswer;
    }

    public String getCoreAnswer() {
        return coreAnswer;
    }

    public void setCoreAnswer(String coreAnswer) {
        this.coreAnswer = coreAnswer;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMatchedChunkCount() {
        return matchedChunkCount;
    }

    public void setMatchedChunkCount(int matchedChunkCount) {
        this.matchedChunkCount = matchedChunkCount;
    }

    public List<RagChunkResult> getReferences() {
        return references;
    }

    public void setReferences(List<RagChunkResult> references) {
        this.references = references;
    }

    public List<Long> getRelatedStoryIds() {
        return relatedStoryIds;
    }

    public void setRelatedStoryIds(List<Long> relatedStoryIds) {
        this.relatedStoryIds = relatedStoryIds;
    }

    public List<Long> getUsedStoryIds() {
        return usedStoryIds;
    }

    public void setUsedStoryIds(List<Long> usedStoryIds) {
        this.usedStoryIds = usedStoryIds;
    }

    public Long getPrimaryStoryId() {
        return primaryStoryId;
    }

    public void setPrimaryStoryId(Long primaryStoryId) {
        this.primaryStoryId = primaryStoryId;
    }

    public RagChunkResult getPrimaryReference() {
        return primaryReference;
    }

    public void setPrimaryReference(RagChunkResult primaryReference) {
        this.primaryReference = primaryReference;
    }
}