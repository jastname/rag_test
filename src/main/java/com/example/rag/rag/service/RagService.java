package com.example.rag.rag.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rag.common.config.RagProperties;
import com.example.rag.rag.dto.RagAskResponse;
import com.example.rag.rag.dto.RagChunkResult;
import com.example.rag.rag.dto.RagIndexResponse;
import com.example.rag.rag.mapper.RagMapper;
import com.example.rag.rag.model.RagStorySource;
import com.example.rag.rag.model.StoryChunk;
import com.example.rag.rag.model.StoryVector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String THINK_END_TAG = "</think>";

    private final RagMapper ragMapper;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final AnswerGenerationService answerGenerationService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public RagService(
            RagMapper ragMapper,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            AnswerGenerationService answerGenerationService,
            RagProperties ragProperties,
            ObjectMapper objectMapper
    ) {
        this.ragMapper = ragMapper;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.answerGenerationService = answerGenerationService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RagIndexResponse embedAllStories() {
        RagIndexResponse response = rebuildIndex();
        response.setMessage("story DB 전체 조회 후 chunk 생성과 임베딩 저장을 완료했습니다.");
        return response;
    }

    @Transactional
    public RagIndexResponse rebuildIndex() {
        List<RagStorySource> stories = ragMapper.findAllStoriesForRag();
        ragMapper.deleteAllStoryVectors();
        ragMapper.deleteAllStoryChunks();

        int chunkCount = 0;
        int vectorCount = 0;

        for (RagStorySource story : stories) {
            List<String> chunks = textChunker.chunk(buildChunkSourceText(story));
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                StoryChunk storyChunk = new StoryChunk();
                storyChunk.setStoryId(story.getStoryId());
                storyChunk.setChunkIndex(i);
                storyChunk.setChunkText(chunkText);
                ragMapper.insertStoryChunk(storyChunk);
                chunkCount++;

                StoryVector storyVector = new StoryVector();
                storyVector.setChunkId(storyChunk.getChunkId());
                storyVector.setStoryId(story.getStoryId());
                storyVector.setEmbeddingModel(embeddingService.modelName());
                storyVector.setVectorJson(toJson(embeddingService.embed(chunkText)));
                ragMapper.insertStoryVector(storyVector);
                vectorCount++;
            }
        }

        log.info("[RAG] 인덱스 재생성 완료 stories={} chunks={} vectors={}", stories.size(), chunkCount, vectorCount);

        RagIndexResponse response = new RagIndexResponse();
        response.setSuccess(true);
        response.setMessage("RAG 인덱스 재생성을 완료했습니다.");
        response.setStoryCount(stories.size());
        response.setChunkCount(chunkCount);
        response.setVectorCount(vectorCount);
        return response;
    }

    public RagAskResponse ask(String question, Integer requestedTopK) {
        int topK = requestedTopK == null ? ragProperties.getTopK() : requestedTopK;
        List<RagChunkResult> references = search(question, topK);
        String generatedAnswer = answerGenerationService.generateAnswer(question, references);
        AnswerParts answerParts = splitAnswerParts(generatedAnswer);

        RagAskResponse response = new RagAskResponse();
        response.setSuccess(true);
        response.setQuestion(question);
        response.setTopK(topK);
        response.setMatchedChunkCount(references.size());
        response.setReferences(references);
        response.setRawAnswer(answerParts.rawAnswer());
        response.setThinkAnswer(answerParts.thinkAnswer());
        response.setCoreAnswer(answerParts.coreAnswer());
        response.setAnswer(answerParts.coreAnswer());
        return response;
    }

    public List<RagChunkResult> search(String question, Integer requestedTopK) {
        int topK = requestedTopK == null ? ragProperties.getTopK() : requestedTopK;
        double[] queryVector = embeddingService.embed(question);

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (StoryChunk chunk : ragMapper.findAllChunksWithVectors()) {
            double[] chunkVector = fromJson(chunk.getVectorJson());
            double similarity = cosineSimilarity(queryVector, chunkVector);

            RagChunkResult result = new RagChunkResult();
            result.setStoryId(chunk.getStoryId());
            result.setChunkId(chunk.getChunkId());
            result.setChunkIndex(chunk.getChunkIndex());
            result.setTitle(extractTitle(chunk.getChunkText()));
            result.setChunkText(chunk.getChunkText());
            result.setSourceUrl(extractSourceUrl(chunk.getChunkText()));
            result.setSimilarity(similarity);
            scoredChunks.add(new ScoredChunk(result, similarity));
        }

        return scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, topK))
                .collect(Collectors.mapping(ScoredChunk::result, Collectors.toList()));
    }

    private String buildChunkSourceText(RagStorySource story) {
        return String.join("\n",
                "title: " + safe(story.getTitle()),
                "description: " + safe(story.getDescription()));
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("벡터 JSON 변환에 실패했습니다.", e);
        }
    }

    private double[] fromJson(String vectorJson) {
        try {
            List<Double> values = objectMapper.readValue(vectorJson, new TypeReference<List<Double>>() { });
            double[] vector = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }
            return vector;
        } catch (Exception e) {
            throw new IllegalStateException("벡터 JSON 파싱에 실패했습니다.", e);
        }
    }

    private double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String extractTitle(String chunkText) {
        return extractField(chunkText, "title: ");
    }

    private String extractSourceUrl(String chunkText) {
        return extractField(chunkText, "url: ");
    }

    private String extractField(String chunkText, String prefix) {
        if (chunkText == null) {
            return "";
        }
        for (String line : chunkText.split("\\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private AnswerParts splitAnswerParts(String answer) {
        String safeAnswer = answer == null ? "" : answer.trim();
        int thinkEndIndex = safeAnswer.indexOf(THINK_END_TAG);
        if (thinkEndIndex < 0) {
            return new AnswerParts(safeAnswer, "", safeAnswer);
        }

        String thinkPart = safeAnswer.substring(0, thinkEndIndex).trim();
        thinkPart = thinkPart.replace("<think>", "").trim();
        String corePart = safeAnswer.substring(thinkEndIndex + THINK_END_TAG.length()).trim();
        if (corePart.isEmpty()) {
            corePart = safeAnswer;
        }
        return new AnswerParts(safeAnswer, thinkPart, corePart);
    }

    private record ScoredChunk(RagChunkResult result, double score) {
    }

    private record AnswerParts(String rawAnswer, String thinkAnswer, String coreAnswer) {
    }
}
