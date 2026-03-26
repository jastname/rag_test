package com.example.rag.story.dto;

import java.time.LocalDateTime;

public class StoryDetailResponse {

    private Long storyId;
    private Long collectId;
    private String title;
    private String alternativeTitle;
    private String creator;
    private String regDateRaw;
    private String collectionDb;
    private String subjectCategory;
    private String subjectKeyword;
    private String extent;
    private String description;
    private String spatialCoverage;
    private String temporalInfo;
    private String personInfo;
    private String language;
    private String sourceTitle;
    private String referenceIdentifier;
    private String rightsInfo;
    private String copyrightOthers;
    private String contentUrl;
    private String contributor;
    private String hashKey;
    private String useYn;
    private LocalDateTime regDt;
    private LocalDateTime updDt;

    public Long getStoryId() {
        return storyId;
    }

    public void setStoryId(Long storyId) {
        this.storyId = storyId;
    }

    public Long getCollectId() {
        return collectId;
    }

    public void setCollectId(Long collectId) {
        this.collectId = collectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlternativeTitle() {
        return alternativeTitle;
    }

    public void setAlternativeTitle(String alternativeTitle) {
        this.alternativeTitle = alternativeTitle;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getRegDateRaw() {
        return regDateRaw;
    }

    public void setRegDateRaw(String regDateRaw) {
        this.regDateRaw = regDateRaw;
    }

    public String getCollectionDb() {
        return collectionDb;
    }

    public void setCollectionDb(String collectionDb) {
        this.collectionDb = collectionDb;
    }

    public String getSubjectCategory() {
        return subjectCategory;
    }

    public void setSubjectCategory(String subjectCategory) {
        this.subjectCategory = subjectCategory;
    }

    public String getSubjectKeyword() {
        return subjectKeyword;
    }

    public void setSubjectKeyword(String subjectKeyword) {
        this.subjectKeyword = subjectKeyword;
    }

    public String getExtent() {
        return extent;
    }

    public void setExtent(String extent) {
        this.extent = extent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpatialCoverage() {
        return spatialCoverage;
    }

    public void setSpatialCoverage(String spatialCoverage) {
        this.spatialCoverage = spatialCoverage;
    }

    public String getTemporalInfo() {
        return temporalInfo;
    }

    public void setTemporalInfo(String temporalInfo) {
        this.temporalInfo = temporalInfo;
    }

    public String getPersonInfo() {
        return personInfo;
    }

    public void setPersonInfo(String personInfo) {
        this.personInfo = personInfo;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getReferenceIdentifier() {
        return referenceIdentifier;
    }

    public void setReferenceIdentifier(String referenceIdentifier) {
        this.referenceIdentifier = referenceIdentifier;
    }

    public String getRightsInfo() {
        return rightsInfo;
    }

    public void setRightsInfo(String rightsInfo) {
        this.rightsInfo = rightsInfo;
    }

    public String getCopyrightOthers() {
        return copyrightOthers;
    }

    public void setCopyrightOthers(String copyrightOthers) {
        this.copyrightOthers = copyrightOthers;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }

    public String getContributor() {
        return contributor;
    }

    public void setContributor(String contributor) {
        this.contributor = contributor;
    }

    public String getHashKey() {
        return hashKey;
    }

    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public LocalDateTime getRegDt() {
        return regDt;
    }

    public void setRegDt(LocalDateTime regDt) {
        this.regDt = regDt;
    }

    public LocalDateTime getUpdDt() {
        return updDt;
    }

    public void setUpdDt(LocalDateTime updDt) {
        this.updDt = updDt;
    }
}
