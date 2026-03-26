package com.example.rag.rag.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    // 검색, 인덱싱, 답변 후처리를 한 곳에서 담당하는 핵심 서비스다.
    // 현재 검색 전략은 제목 exact / 제목 부분 / 설명 키워드 / 벡터 후보를 각각 따로 조회한 뒤
    // 같은 chunkId 기준으로 점수를 누적해 최종 순위를 매기는 하이브리드 방식이다.

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String THINK_END_TAG = "</think>";
    private static final Pattern USED_STORY_IDS_PATTERN = Pattern.compile("\\[\\[USED_STORY_IDS:([^\\]]*)\\]\\]");

    private static final double TITLE_EXACT_MATCH_SCORE = 100.0d;
    private static final double TITLE_PARTIAL_MATCH_SCORE = 40.0d;
    private static final double DESCRIPTION_KEYWORD_SCORE = 20.0d;
    private static final double EXPANDED_KEYWORD_SCORE = 12.0d;
    private static final double VECTOR_SIMILARITY_WEIGHT = 30.0d;
    private static final int DEFAULT_KEYWORD_LIMIT = 20;
    private static final int DEFAULT_VECTOR_CANDIDATE_LIMIT = 50;
    private static final List<String> TITLE_QUERY_SUFFIXES = List.of(
            "은 어떤 동화야", "는 어떤 동화야", "이 어떤 동화야", "가 어떤 동화야",
            "은 무슨 동화야", "는 무슨 동화야", "이 무슨 동화야", "가 무슨 동화야",
            "은 어떤 이야기야", "는 어떤 이야기야", "이 어떤 이야기야", "가 어떤 이야기야",
            "은 무슨 이야기야", "는 무슨 이야기야", "이 무슨 이야기야", "가 무슨 이야기야",
            "은 어떤 동화", "는 어떤 동화", "이 어떤 동화", "가 어떤 동화",
            "은 무슨 동화", "는 무슨 동화", "이 무슨 동화", "가 무슨 동화",
            "은 어떤 이야기", "는 어떤 이야기", "이 어떤 이야기", "가 어떤 이야기",
            "은 무슨 이야기", "는 무슨 이야기", "이 무슨 이야기야", "가 무슨 이야기",
            "이라는 동화", "라는 동화", "이란 동화", "란 동화",
            "이라는 이야기", "라는 이야기", "이란 이야기", "란 이야기",
            "은 뭐야", "는 뭐야", "이 뭐야", "가 뭐야",
            "은 뭐지", "는 뭐지", "이 뭐지", "가 뭐지",
            "은 알려줘", "는 알려줘", "이 알려줘", "가 알려줘",
            "은", "는", "이", "가"
    );

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
        // 외부에서 호출하는 공개 진입점은 단순하게 유지하고,
        // 실제 재색인은 rebuildIndex()에 위임해 재사용성을 높인다.
        RagIndexResponse response = rebuildIndex();
        response.setMessage("story DB 전체 조회 후 chunk 생성과 임베딩 저장을 완료했습니다.");
        return response;
    }

    @Transactional
    public RagIndexResponse rebuildIndex() {
        // 원본 스토리 전체를 다시 읽어 청크/벡터를 재구성한다.
        // 증분 갱신보다 단순하지만, 현재 구조에서는 원본-인덱스 불일치 가능성을 줄이는 데 유리하다.
        List<RagStorySource> stories = ragMapper.findAllStoriesForRag();
        // 기존 청크/벡터를 모두 지우고 다시 적재해 원본 스토리와 인덱스 간 불일치를 줄인다.
        ragMapper.deleteAllStoryVectors();
        ragMapper.deleteAllStoryChunks();

        int chunkCount = 0;
        int vectorCount = 0;

        for (RagStorySource story : stories) {
            // 제목과 설명을 함께 청킹해서
            // 제목 검색과 설명 검색, 벡터 검색 모두 같은 소스 텍스트를 바라보게 한다.
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
        // ask()는 전체 사용자 질의 파이프라인의 진입점이다.
        // 1) 검색 2) LLM 답변 생성 3) think / core / usedStoryIds 후처리를 순서대로 수행한다.
        int topK = requestedTopK == null ? ragProperties.getTopK() : requestedTopK;
        List<RagChunkResult> references = search(question, topK);
        String generatedAnswer = answerGenerationService.generateAnswer(question, references);
        AnswerParts answerParts = splitAnswerParts(generatedAnswer);
        RagChunkResult primaryReference = extractPrimaryReference(references);

        RagAskResponse response = new RagAskResponse();
        response.setSuccess(true);
        response.setQuestion(question);
        response.setTopK(topK);
        response.setMatchedChunkCount(references.size());
        response.setReferences(references);
        response.setRelatedStoryIds(extractRelatedStoryIds(references));
        response.setUsedStoryIds(answerParts.usedStoryIds());
        response.setPrimaryReference(primaryReference);
        response.setPrimaryStoryId(primaryReference == null ? null : primaryReference.getStoryId());
        response.setRawAnswer(answerParts.rawAnswer());
        response.setThinkAnswer(answerParts.thinkAnswer());
        response.setCoreAnswer(answerParts.coreAnswer());
        response.setAnswer(answerParts.coreAnswer());
        return response;
    }

    public List<RagChunkResult> search(String question, Integer requestedTopK) {
        // search()는 질문 하나를 여러 검색 전략으로 분해해서 처리한다.
        // - normalizedQuestion: 비교 일관성을 위한 정규화 질의
        // - titleCandidate: 질문형 표현에서 제목 후보만 잘라낸 값
        // - queryKeywords / expandedKeywords: 설명 검색과 질의 확장용 키워드
        // 최종 결과는 경로별 점수 누적값이 높은 순으로 정렬한다.
        int topK = requestedTopK == null ? ragProperties.getTopK() : requestedTopK;
        double[] queryVector = embeddingService.embed(question);
        String normalizedQuestion = normalize(question);
        String titleCandidate = extractTitleCandidate(question);
        List<String> queryKeywords = extractKeywords(question);
        List<String> expandedKeywords = expandKeywords(question, queryKeywords);
        int keywordLimit = Math.max(DEFAULT_KEYWORD_LIMIT, topK * 4);
        int vectorCandidateLimit = Math.max(DEFAULT_VECTOR_CANDIDATE_LIMIT, topK * 10);
        log.info("[RAG][SEARCH] question='{}', normalized='{}', titleCandidate='{}', queryKeywords={}, expandedKeywords={}, topK={}",
                safe(question), normalizedQuestion, titleCandidate, queryKeywords, expandedKeywords, topK);

        Map<Long, ScoredChunkAccumulator> scoredChunks = new LinkedHashMap<>();
        // 제목 exact → 제목 partial → 설명 keyword → vector 후보 순으로 각각 독립 수집한다.
        // 이 순서는 실행 순서일 뿐, 최종 결과는 누적 점수로 재정렬된다.
        collectExactTitleMatches(scoredChunks, normalizedQuestion, titleCandidate);
        collectTitleKeywordMatches(scoredChunks, normalizedQuestion, titleCandidate, expandedKeywords, keywordLimit);
        collectDescriptionKeywordMatches(scoredChunks, queryKeywords, expandedKeywords, keywordLimit);
        collectVectorCandidates(scoredChunks, queryVector, vectorCandidateLimit);

        List<RagChunkResult> results = scoredChunks.values().stream()
                .map(ScoredChunkAccumulator::toResult)
                .sorted(Comparator.comparingDouble(RagChunkResult::getSimilarity).reversed()
                        .thenComparing(RagChunkResult::getChunkId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(1, topK))
                .toList();
        logTopSearchResults(results);
        return results;
    }

    private void collectExactTitleMatches(Map<Long, ScoredChunkAccumulator> scoredChunks, String normalizedQuestion, String titleCandidate) {
        // 제목 exact는 고유명사 검색에서 가장 신뢰도가 높으므로 가장 큰 가중치를 준다.
        // 질문 전체와 제목 후보를 모두 exact 후보로 본다.
        LinkedHashSet<String> exactQueries = new LinkedHashSet<>();
        if (!normalizedQuestion.isEmpty()) {
            exactQueries.add(normalizedQuestion);
        }
        if (!titleCandidate.isEmpty()) {
            exactQueries.add(titleCandidate);
        }
        for (String exactQuery : exactQueries) {
            for (StoryChunk chunk : ragMapper.findChunksByExactTitle(exactQuery)) {
                accumulateChunkScore(scoredChunks, chunk, TITLE_EXACT_MATCH_SCORE, 1.0d);
            }
        }
    }

    private void collectTitleKeywordMatches(
            Map<Long, ScoredChunkAccumulator> scoredChunks,
            String normalizedQuestion,
            String titleCandidate,
            List<String> expandedKeywords,
            int keywordLimit
    ) {
        // 제목 부분 검색은 exact보다 약하지만,
        // 작품명 뒤에 수식어가 붙은 경우를 복구하는 데 유용하다.
        // 확장 키워드까지 포함해 제목 LIKE 후보를 넓게 수집한다.
        LinkedHashSet<String> titleQueries = new LinkedHashSet<>();
        if (!normalizedQuestion.isEmpty()) {
            titleQueries.add(normalizedQuestion);
        }
        if (!titleCandidate.isEmpty()) {
            titleQueries.add(titleCandidate);
        }
        titleQueries.addAll(expandedKeywords);
        for (String query : titleQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            for (StoryChunk chunk : ragMapper.findChunksByTitleKeyword(query, keywordLimit)) {
                accumulateChunkScore(scoredChunks, chunk, TITLE_PARTIAL_MATCH_SCORE, null);
            }
        }
    }

    private void collectDescriptionKeywordMatches(
            Map<Long, ScoredChunkAccumulator> scoredChunks,
            List<String> queryKeywords,
            List<String> expandedKeywords,
            int keywordLimit
    ) {
        // 설명 검색은 주로 설명형 질의를 보강하는 역할이다.
        // 기본 키워드는 높은 점수, 확장 키워드는 보조 점수만 반영한다.
        LinkedHashSet<String> descriptionQueries = new LinkedHashSet<>(queryKeywords);
        descriptionQueries.addAll(expandedKeywords);
        for (String query : descriptionQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            double keywordScore = queryKeywords.contains(query) ? DESCRIPTION_KEYWORD_SCORE : EXPANDED_KEYWORD_SCORE;
            for (StoryChunk chunk : ragMapper.findChunksByDescriptionKeyword(query, keywordLimit)) {
                accumulateChunkScore(scoredChunks, chunk, keywordScore, null);
            }
        }
    }

    private void collectVectorCandidates(Map<Long, ScoredChunkAccumulator> scoredChunks, double[] queryVector, int vectorCandidateLimit) {
        // 벡터 후보는 의미 유사성을 보완하는 마지막 축이다.
        // 현재는 후보 청크를 가져온 뒤 Java에서 cosine similarity를 계산해 누적한다.
        // 추후 pgvector distance 정렬로 옮기면 성능을 더 개선할 수 있다.
        for (StoryChunk chunk : ragMapper.findVectorCandidateChunks(vectorCandidateLimit)) {
            double[] chunkVector = fromJson(chunk.getVectorJson());
            double similarity = cosineSimilarity(queryVector, chunkVector);
            double weightedVectorScore = clampSimilarity(similarity) * VECTOR_SIMILARITY_WEIGHT;
            accumulateChunkScore(scoredChunks, chunk, weightedVectorScore, similarity);
        }
    }

    private void accumulateChunkScore(
            Map<Long, ScoredChunkAccumulator> scoredChunks,
            StoryChunk chunk,
            double scoreDelta,
            Double similarityOverride
    ) {
        // 검색 경로가 여러 개여도 chunkId가 같으면 하나의 누적 버킷으로 합친다.
        // similarityOverride는 벡터 검색처럼 별도 수치가 있을 때만 갱신한다.
        if (chunk == null || chunk.getChunkId() == null) {
            return;
        }
        ScoredChunkAccumulator accumulator = scoredChunks.computeIfAbsent(chunk.getChunkId(), ignored -> createAccumulator(chunk));
        accumulator.addScore(scoreDelta);
        if (similarityOverride != null) {
            accumulator.updateSimilarity(similarityOverride);
        }
    }

    private ScoredChunkAccumulator createAccumulator(StoryChunk chunk) {
        // DB 모델인 StoryChunk를 API 응답 모델인 RagChunkResult로 변환하는 초기화 지점이다.
        // 이후 경로별 점수는 accumulator가 들고 있고, 마지막에 toResult()로 합친다.
        String chunkText = safe(chunk.getChunkText());
        RagChunkResult result = new RagChunkResult();
        result.setStoryId(chunk.getStoryId());
        result.setChunkId(chunk.getChunkId());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setTitle(extractTitle(chunkText));
        result.setChunkText(chunkText);
        result.setSourceUrl(extractSourceUrl(chunkText));
        result.setSimilarity(0.0d);
        return new ScoredChunkAccumulator(result);
    }

    private double calculateHybridScore(
            String normalizedQuestion,
            String titleCandidate,
            List<String> queryKeywords,
            List<String> expandedKeywords,
            String normalizedTitle,
            String normalizedDescription,
            double similarity
    ) {
        double score = clampSimilarity(similarity) * VECTOR_SIMILARITY_WEIGHT;

        if (isExactTitleMatch(normalizedQuestion, titleCandidate, normalizedTitle)) {
            score += TITLE_EXACT_MATCH_SCORE;
        } else if (isPartialTitleMatch(normalizedQuestion, titleCandidate, normalizedTitle)) {
            score += TITLE_PARTIAL_MATCH_SCORE;
        }

        if (containsAnyKeyword(normalizedDescription, queryKeywords)) {
            score += DESCRIPTION_KEYWORD_SCORE;
        }
        if (containsAnyKeyword(normalizedTitle, expandedKeywords) || containsAnyKeyword(normalizedDescription, expandedKeywords)) {
            score += EXPANDED_KEYWORD_SCORE;
        }
        return score;
    }

    private List<String> expandKeywords(String question, List<String> baseKeywords) {
        // 짧은 제목형 질의는 그대로 임베딩하면 문맥이 약해질 수 있어
        // '이야기', '전래동화' 같은 힌트를 내부적으로 붙여 설명 검색 후보를 넓힌다.
        LinkedHashSet<String> expanded = new LinkedHashSet<>(baseKeywords);
        String normalizedQuestion = normalize(question);
        String titleCandidate = extractTitleCandidate(question);
        for (String keyword : baseKeywords) {
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            expanded.add(trimmed);
            expanded.add(trimmed + " 이야기");
            expanded.add(trimmed + " 전래동화");
        }
        if (!titleCandidate.isEmpty()) {
            expanded.add(titleCandidate);
            if (!titleCandidate.equals(normalizedQuestion)) {
                expanded.add(titleCandidate + " 이야기");
                expanded.add(titleCandidate + " 전래동화");
            }
        }
        boolean shortQuery = baseKeywords.size() <= 1 && normalizedQuestion.replace(" ", "").length() <= 8;
        if (shortQuery) {
            expanded.add(normalizedQuestion);
            expanded.add(normalizedQuestion + " 이야기");
            expanded.add(normalizedQuestion + " 전래동화");
        }
        return new ArrayList<>(expanded);
    }

    private List<String> extractKeywords(String question) {
        // 현재 키워드 추출은 형태소 분석 없이 가벼운 정규화 + 공백 분리 전략을 사용한다.
        // 길이 2 미만 토큰은 노이즈로 보고 제거한다.
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalizedQuestion.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private boolean containsAnyKeyword(String content, List<String> keywords) {
        if (content == null || content.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExactTitleMatch(String normalizedQuestion, String titleCandidate, String normalizedTitle) {
        return !normalizedTitle.isEmpty()
                && (normalizedTitle.equals(normalizedQuestion) || (!titleCandidate.isEmpty() && normalizedTitle.equals(titleCandidate)));
    }

    private boolean isPartialTitleMatch(String normalizedQuestion, String titleCandidate, String normalizedTitle) {
        if (normalizedTitle.isEmpty()) {
            return false;
        }
        if (!normalizedQuestion.isEmpty() && normalizedTitle.contains(normalizedQuestion)) {
            return true;
        }
        return !titleCandidate.isEmpty() && (normalizedTitle.contains(titleCandidate) || titleCandidate.contains(normalizedTitle));
    }

    private String extractTitleCandidate(String question) {
        // '금도끼와 은도끼는 어떤동화야' 같은 질문형 문장에서
        // 뒤쪽 질문 꼬리표를 제거해 작품명 후보를 복원한다.
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.isEmpty()) {
            return "";
        }
        String candidate = normalizedQuestion;
        for (String suffix : TITLE_QUERY_SUFFIXES) {
            if (candidate.endsWith(suffix)) {
                candidate = candidate.substring(0, candidate.length() - suffix.length()).trim();
                break;
            }
        }
        if (candidate.isEmpty() || candidate.equals(normalizedQuestion)) {
            return candidate;
        }
        return candidate;
    }

    private String extractDescription(String chunkText) {
        return extractField(chunkText, "description: ");
    }

    private String normalize(String value) {
        // 검색 비교에 일관성을 주기 위해 영문 소문자화, 특수문자 제거, 공백 정리를 수행한다.
        // 한글/영문/숫자만 남기고 나머지는 공백으로 치환한다.
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s가-힣]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double clampSimilarity(double similarity) {
        if (Double.isNaN(similarity) || Double.isInfinite(similarity)) {
            return 0.0d;
        }
        return Math.max(0.0d, similarity);
    }

    private RagChunkResult extractPrimaryReference(List<RagChunkResult> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        return references.get(0);
    }

    private List<Long> extractRelatedStoryIds(List<RagChunkResult> references) {
        LinkedHashSet<Long> storyIds = new LinkedHashSet<>();
        for (RagChunkResult reference : references) {
            if (reference.getStoryId() != null) {
                storyIds.add(reference.getStoryId());
            }
        }
        return new ArrayList<>(storyIds);
    }

    private String buildChunkSourceText(RagStorySource story) {
        return String.join("\n",
                "title: " + safe(story.getTitle()),
                "storyTitle: " + safe(story.getTitle()),
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
        // 길이가 다른 벡터가 들어와도 공통 길이까지만 계산한다.
        // 어느 한쪽이 영벡터면 유사도를 0으로 처리해 NaN 전파를 막는다.
        int length = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        // 어느 한쪽 벡터라도 영벡터면 유사도를 0으로 본다.
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
        // 모델 응답에서 think 블록과 실제 사용자 노출 답변을 분리한다.
        // storyId 메타데이터 제거도 이 단계 이전에 함께 처리한다.
        String safeAnswer = answer == null ? "" : answer.trim();
        ParsedUsedStoryIds parsedUsedStoryIds = extractUsedStoryIds(safeAnswer);
        String cleanedAnswer = parsedUsedStoryIds.cleanedAnswer();
        int thinkEndIndex = cleanedAnswer.indexOf(THINK_END_TAG);
        if (thinkEndIndex < 0) {
            return new AnswerParts(safeAnswer, "", cleanedAnswer, parsedUsedStoryIds.usedStoryIds());
        }

        // 모델 응답에 <think>...</think> 형식이 섞여 오면 표시용으로 사고과정과 최종 답변을 분리한다.
        String thinkPart = cleanedAnswer.substring(0, thinkEndIndex).trim();
        thinkPart = thinkPart.replace("<think>", "").trim();
        String corePart = cleanedAnswer.substring(thinkEndIndex + THINK_END_TAG.length()).trim();
        if (corePart.isEmpty()) {
            corePart = cleanedAnswer;
        }
        return new AnswerParts(safeAnswer, thinkPart, corePart, parsedUsedStoryIds.usedStoryIds());
    }

    private ParsedUsedStoryIds extractUsedStoryIds(String answer) {
        // 답변 뒤에 붙는 [[USED_STORY_IDS:...]] 블록을 파싱한다.
        // 잘못된 토큰이 섞여 있어도 가능한 값만 복구하도록 느슨하게 처리한다.
        if (answer == null) {
            return new ParsedUsedStoryIds("", List.of());
        }
        Matcher matcher = USED_STORY_IDS_PATTERN.matcher(answer);
        if (!matcher.find()) {
            return new ParsedUsedStoryIds(answer.trim(), List.of());
        }

        String cleanedAnswer = matcher.replaceFirst("").trim();
        String idGroup = matcher.group(1) == null ? "" : matcher.group(1).trim();
        if (idGroup.isEmpty()) {
            return new ParsedUsedStoryIds(cleanedAnswer, List.of());
        }

        LinkedHashSet<Long> storyIds = new LinkedHashSet<>();
        for (String token : idGroup.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                storyIds.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 모델이 잘못된 값을 넣으면 해당 토큰만 버리고 나머지는 최대한 살린다.
            }
        }
        return new ParsedUsedStoryIds(cleanedAnswer, new ArrayList<>(storyIds));
    }

    private void logTopSearchResults(List<RagChunkResult> results) {
        // 운영 중 검색 디버깅을 쉽게 하기 위해 상위 후보를 한 줄 요약으로 남긴다.
        // 점수 상세가 아니라 storyId / chunkId / title / 최종 score 성격의 similarity 값을 출력한다.
        if (results == null || results.isEmpty()) {
            log.info("[RAG][SEARCH] no search results");
            return;
        }
        String summary = results.stream()
                .map(result -> "storyId=" + safeId(result.getStoryId())
                        + ", chunkId=" + safeId(result.getChunkId())
                        + ", title='" + safe(result.getTitle()) + "'"
                        + ", similarity=" + result.getSimilarity())
                .collect(Collectors.joining(" | "));
        log.info("[RAG][SEARCH] top results: {}", summary);
    }

    private String safeId(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class ScoredChunkAccumulator {
        // 분리 검색 경로에서 모인 점수를 chunk 단위로 합치는 내부 버퍼다.
        // API 응답 객체를 미리 들고 있으면서 score와 vector similarity를 누적한다.
        private final RagChunkResult result;
        private double score;
        private double similarity;

        private ScoredChunkAccumulator(RagChunkResult result) {
            this.result = result;
        }

        private void addScore(double delta) {
            this.score += delta;
        }

        private void updateSimilarity(double candidateSimilarity) {
            this.similarity = Math.max(this.similarity, candidateSimilarity);
        }

        private RagChunkResult toResult() {
            // 현재 응답 DTO에 별도 totalScore 필드가 없어서
            // 정렬/표시용 총점을 similarity 필드에 덮어쓴다.
            // 클라이언트가 실제 cosine similarity만 기대한다면 향후 필드 분리를 고려해야 한다.
            result.setSimilarity(score + similarity);
            return result;
        }
    }

    private record ParsedUsedStoryIds(String cleanedAnswer, List<Long> usedStoryIds) {
    }

    private record AnswerParts(String rawAnswer, String thinkAnswer, String coreAnswer, List<Long> usedStoryIds) {
    }
}