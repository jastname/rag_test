package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.example.rag.rag.dto.RagChunkResult;

class AnswerGenerationServiceTest {

    @Test
    void generateAnswerBuildsPromptAndReturnsModelText() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("요약 답변입니다.")))));

        AnswerGenerationService service = new AnswerGenerationService(chatModel);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setStoryId(173L);
        chunk.setTitle("설화 제목");
        chunk.setChunkText("title: 설화 제목\nstoryTitle: 설화 제목\ndescription: 옛날 옛적 이야기입니다.");
        chunk.setSourceUrl("https://example.com/story");
        chunk.setSimilarity(0.91d);

        String answer = service.generateAnswer("무슨 이야기야?", List.of(chunk));

        assertThat(answer).isEqualTo("요약 답변입니다.");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateAnswerUsesOnlyDescriptionInsidePrompt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("응답")))));

        AnswerGenerationService service = new AnswerGenerationService(chatModel);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setStoryId(55L);
        chunk.setTitle("슬기로운 효자");
        chunk.setChunkText("title: 슬기로운 효자\nstoryTitle: 슬기로운 효자\ndescription: 옛날에 한 부부가 살았어요.");

        service.generateAnswer("이야기 요약해줘", List.of(chunk));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getContents();

        assertThat(promptText).contains("이야기 요약해줘")
                .contains("#1 [storyId=55] 슬기로운 효자")
                .contains("<pre>옛날에 한 부부가 살았어요.</pre>")
                .contains("[[USED_STORY_IDS:id1,id2]]")
                .contains("[[USED_STORY_IDS:]]")
                .doesNotContain("storyTitle: 슬기로운 효자")
                .doesNotContain("title: 슬기로운 효자")
                .doesNotContain("description: 옛날에 한 부부가 살았어요.");
    }

    @Test
    void generateAnswerFailsWhenResponseIsBlank() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));

        AnswerGenerationService service = new AnswerGenerationService(chatModel);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkText("문맥");

        assertThatThrownBy(() -> service.generateAnswer("질문", List.of(chunk)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("텍스트 값이 없습니다");
    }
}