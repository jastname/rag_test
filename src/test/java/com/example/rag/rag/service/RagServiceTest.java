package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void askSplitsThinkAndCoreAnswerWhenThinkTagExists() {
        RagService service = createService("<think>분석 내용</think>최종 답변", List.of(createChunk()));

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getRawAnswer()).isEqualTo("<think>분석 내용</think>최종 답변");
        assertThat(response.getThinkAnswer()).isEqualTo("분석 내용");
        assertThat(response.getCoreAnswer()).isEqualTo("최종 답변");
        assertThat(response.getAnswer()).isEqualTo("최종 답변");
    }

    @Test
    void askKeepsWholeAnswerAsCoreWhenThinkTagDoesNotExist() {
        RagService service = createService("일반 답변", List.of(createChunk()));

        RagAskResponse response = service.ask("질문", 3);

        assertThat(response.getRawAnswer()).isEqualTo("일반 답변");
        assertThat(response.getThinkAnswer()).isEmpty();
        assertThat(response.getCoreAnswer()).isEqualTo("일반 답변");
        assertThat(response.getAnswer()).isEqualTo("일반 답변");
    }

    private RagService createService(String generatedAnswer, List<StoryChunk> chunks) {
        RagMapper ragMapper = mock(RagMapper.class);
        when(ragMapper.findAllChunksWithVectors()).thenReturn(chunks);

        TextChunker textChunker = mock(TextChunker.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("질문")).thenReturn(new double[] {1.0d, 0.0d});
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

    private StoryChunk createChunk() {
        StoryChunk chunk = new StoryChunk();
        chunk.setStoryId(1L);
        chunk.setChunkId(10L);
        chunk.setChunkIndex(0);
        chunk.setChunkText("title: 테스트 제목\nurl: https://example.com\ndescription: 테스트 본문");
        chunk.setVectorJson("[1.0,0.0]");
        return chunk;
    }
}
