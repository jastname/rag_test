package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.rag.common.config.RagProperties;
import com.example.rag.rag.dto.RagAskResponse;
import com.example.rag.rag.mapper.RagMapper;
import com.example.rag.rag.model.StoryChunk;
import com.fasterxml.jackson.databind.ObjectMapper;

class RagServiceTest {

    // RagService의 핵심 책임은 두 갈래다.
    // 1) 검색 결과 정렬/선택 2) 생성 답변 후처리(think 분리, storyId 파싱)
    // 이 테스트 클래스는 그 두 축이 깨지지 않는지 시나리오별로 검증한다.

    @Test
    void askSplitsThinkAndCoreAnswerWhenThinkTagExists() {
        // 모델이 <think>...</think> 와 USED_STORY_IDS 메타데이터를 함께 돌려준 경우
        // 사용자 노출 답변과 내부 추적 정보를 정확히 분리하는지 검증한다.
        RagService service = createService("<think>분석 내용</think>최종 답변 [[USED_STORY_IDS:173,214,173]]", List.of(createChunk(173L, 10L), createChunk(173L, 11L), createChunk(214L, 12L)));

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getRawAnswer()).isEqualTo("<think>분석 내용</think>최종 답변 [[USED_STORY_IDS:173,214,173]]");
        assertThat(response.getThinkAnswer()).isEqualTo("분석 내용");
        assertThat(response.getCoreAnswer()).isEqualTo("최종 답변");
        assertThat(response.getAnswer()).isEqualTo("최종 답변");
        assertThat(response.getRelatedStoryIds()).containsExactly(173L, 214L);
        assertThat(response.getUsedStoryIds()).containsExactly(173L, 214L);
        assertThat(response.getPrimaryStoryId()).isEqualTo(173L);
        assertThat(response.getPrimaryReference()).isNotNull();
        assertThat(response.getPrimaryReference().getStoryId()).isEqualTo(173L);
        assertThat(response.getPrimaryReference().getChunkId()).isEqualTo(10L);
    }

    @Test
    void askKeepsWholeAnswerAsCoreWhenThinkTagDoesNotExist() {
        // think 태그가 없는 일반 응답은 본문 전체를 core answer 로 유지해야 한다.
        RagService service = createService("일반 답변 [[USED_STORY_IDS:1]]", List.of(createChunk(1L, 10L)));

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getRawAnswer()).isEqualTo("일반 답변 [[USED_STORY_IDS:1]]");
        assertThat(response.getThinkAnswer()).isEmpty();
        assertThat(response.getCoreAnswer()).isEqualTo("일반 답변");
        assertThat(response.getAnswer()).isEqualTo("일반 답변");
        assertThat(response.getRelatedStoryIds()).containsExactly(1L);
        assertThat(response.getUsedStoryIds()).containsExactly(1L);
        assertThat(response.getPrimaryStoryId()).isEqualTo(1L);
        assertThat(response.getPrimaryReference()).isNotNull();
        assertThat(response.getPrimaryReference().getChunkId()).isEqualTo(10L);
    }

    @Test
    void askReturnsEmptyUsedStoryIdsWhenDelimiterIsMissingOrInvalid() {
        // 모델이 storyId 블록 포맷을 잘못 만들어도 가능한 한 안전하게 복구해야 한다.
        // 숫자로 파싱되지 않는 토큰은 버리고, 결과는 빈 목록으로 처리한다.
        RagService service = createService("일반 답변 [[USED_STORY_IDS:abc, ,not-number]]", List.of(createChunk(1L, 10L)));

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getCoreAnswer()).isEqualTo("일반 답변");
        assertThat(response.getUsedStoryIds()).isEmpty();
    }

    @Test
    void askReturnsNullPrimaryStoryWhenNoReferencesExist() {
        // 검색 결과가 전혀 없으면 대표 근거도 없어야 한다.
        // null/empty 응답 필드 계약이 유지되는지 확인한다.
        RagService service = createService("근거 없는 답변", List.of());

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getMatchedChunkCount()).isZero();
        assertThat(response.getRelatedStoryIds()).isEmpty();
        assertThat(response.getUsedStoryIds()).isEmpty();
        assertThat(response.getPrimaryStoryId()).isNull();
        assertThat(response.getPrimaryReference()).isNull();
    }

    @Test
    void searchPrefersExactTitleMatchForTitleOnlyQuery() {
        // 제목형 질의는 벡터보다 제목 exact 점수가 우선해야 한다.
        RagService service = createService("응답", List.of(
                createChunk(1L, 10L, "title: 해치\ndescription: 정의를 지키는 상상 속 동물 이야기", "[0.2,0.0]"),
                createChunk(2L, 20L, "title: 정의로운 아이\ndescription: 해치가 잠깐 나오는 이야기", "[1.0,0.0]")
        ));

        List<com.example.rag.rag.dto.RagChunkResult> results = service.search("해치", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStoryId()).isEqualTo(1L);
        assertThat(results.get(0).getTitle()).isEqualTo("해치");
    }

    @Test
    void searchFindsDescriptionMatchForDescriptionOnlyQuery() {
        // 설명형 질의는 제목보다 description keyword 경로가 강하게 반영돼야 한다.
        RagService service = createService("응답", List.of(
                createChunk(1L, 10L, "title: 산속 도깨비\ndescription: 욕심 많은 사람 이야기", "[0.1,0.0]"),
                createChunk(2L, 20L, "title: 억울한 사람을 도와주는 도깨비\ndescription: 억울한 사람을 도와주는 전래동화", "[0.8,0.0]")
        ));

        List<com.example.rag.rag.dto.RagChunkResult> results = service.search("억울한 사람을 도와주는 동화", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStoryId()).isEqualTo(2L);
        assertThat(results.get(0).getTitle()).contains("억울한 사람");
    }

    @Test
    void searchCombinesTitleAndSemanticSignalsForMixedQuery() {
        // 혼합형 질의는 제목 신호와 설명/벡터 신호가 함께 반영돼야 한다.
        RagService service = createService("응답", List.of(
                createChunk(1L, 10L, "title: 해치\ndescription: 정의를 지키는 이야기", "[0.7,0.0]"),
                createChunk(2L, 20L, "title: 용감한 호랑이\ndescription: 정의를 지키는 해치 같은 전래동화", "[1.0,0.0]"),
                createChunk(3L, 30L, "title: 착한 선비\ndescription: 평범한 옛날 이야기", "[0.9,0.0]")
        ));

        List<com.example.rag.rag.dto.RagChunkResult> results = service.search("해치처럼 정의를 지키는 이야기", 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getStoryId()).isEqualTo(1L);
        assertThat(results.get(1).getStoryId()).isEqualTo(2L);
    }

    @Test
    void searchPrefersTitleCandidateForQuestionStyleTitleQuery() {
        // 질문형 꼬리표가 붙은 작품명 질의에서도 titleCandidate 복원이 먹혀야 한다.
        RagService service = createService("응답", List.of(
                createChunk(1L, 10L, "title: 금도끼와 은도끼\ndescription: 금도끼와 은도끼에 관한 전래동화", "[0.1,0.0]"),
                createChunk(2L, 20L, "title: 나무꾼 이야기\ndescription: 금도끼를 줍는 일반 이야기", "[1.0,0.0]")
        ));

        List<com.example.rag.rag.dto.RagChunkResult> results = service.search("금도끼와 은도끼는 어떤동화야", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStoryId()).isEqualTo(1L);
        assertThat(results.get(0).getTitle()).isEqualTo("금도끼와 은도끼");
    }

    private RagService createService(String generatedAnswer, List<StoryChunk> chunks) {
        // 실제 DB 대신 mock 매퍼를 사용해 검색 경로별 반환값을 통제한다.
        // 이렇게 하면 title exact / title like / description like / vector 후보가
        // 어떤 식으로 합산되는지 테스트에서 결정론적으로 검증할 수 있다.
        RagMapper ragMapper = mock(RagMapper.class);
        when(ragMapper.findAllChunksWithVectors()).thenReturn(chunks);
        when(ragMapper.findVectorCandidateChunks(anyString(), anyInt())).thenReturn(chunks);
        when(ragMapper.findChunksByExactTitle(anyString())).thenAnswer(invocation -> {
            String title = invocation.getArgument(0, String.class);
            return chunks.stream()
                    .filter(chunk -> extractTitle(chunk).equals(title))
                    .toList();
        });
        when(ragMapper.findChunksByTitleKeyword(anyString(), anyInt())).thenAnswer(invocation -> {
            String keyword = invocation.getArgument(0, String.class);
            return chunks.stream()
                    .filter(chunk -> extractTitle(chunk).contains(keyword))
                    .toList();
        });
        when(ragMapper.findChunksByDescriptionKeyword(anyString(), anyInt())).thenAnswer(invocation -> {
            String keyword = invocation.getArgument(0, String.class);
            return chunks.stream()
                    .filter(chunk -> extractDescription(chunk).contains(keyword))
                    .toList();
        });

        TextChunker textChunker = mock(TextChunker.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        // 테스트마다 질문별 임베딩을 고정해 벡터 점수의 영향도 예측 가능하게 만든다.
        when(embeddingService.embed("질문")).thenReturn(new double[] {1.0d, 0.0d});
        when(embeddingService.embed("해치")).thenReturn(new double[] {1.0d, 0.0d});
        when(embeddingService.embed("억울한 사람을 도와주는 동화")).thenReturn(new double[] {1.0d, 0.0d});
        when(embeddingService.embed("해치처럼 정의를 지키는 이야기")).thenReturn(new double[] {1.0d, 0.0d});
        when(embeddingService.embed("금도끼와 은도끼는 어떤동화야")).thenReturn(new double[] {1.0d, 0.0d});
        AnswerGenerationService answerGenerationService = mock(AnswerGenerationService.class);
        when(answerGenerationService.generateAnswer(org.mockito.ArgumentMatchers.eq("질문"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(generatedAnswer);

        RagProperties ragProperties = new RagProperties();
        ragProperties.setTopK(5);

        return new RagService(
                ragMapper,
                textChunker,
                embeddingService,
                answerGenerationService,
                ragProperties,
                new ObjectMapper()
        );
    }

    private String extractTitle(StoryChunk chunk) {
        // mock 매퍼 내부에서 제목 검색 결과를 흉내내기 위한 헬퍼다.
        // 실제 서비스와 비슷하게 chunkText 구조에서 title 라인을 뽑는다.
        return extractField(chunk.getChunkText(), "title: ");
    }

    private String extractDescription(StoryChunk chunk) {
        // description LIKE 검색을 흉내내기 위한 헬퍼다.
        return extractField(chunk.getChunkText(), "description: ");
    }

    private String extractField(String chunkText, String prefix) {
        // 테스트용 구조화 텍스트 파서.
        // chunkText 포맷이 바뀌면 테스트도 함께 깨져서 계약 변화가 드러난다.
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

    private StoryChunk createChunk(Long storyId, Long chunkId, String chunkText, String vectorJson) {
        // 테스트 데이터 생성을 단순화하는 팩토리 메서드다.
        StoryChunk chunk = new StoryChunk();
        chunk.setStoryId(storyId);
        chunk.setChunkId(chunkId);
        chunk.setChunkIndex(0);
        chunk.setChunkText(chunkText);
        chunk.setVectorJson(vectorJson);
        return chunk;
    }

    private StoryChunk createChunk(Long storyId, Long chunkId) {
        return createChunk(storyId, chunkId, "title: 테스트 제목\nurl: https://example.com\ndescription: 테스트 본문", "[1.0,0.0]");
    }
}
