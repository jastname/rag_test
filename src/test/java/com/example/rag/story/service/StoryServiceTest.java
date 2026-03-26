package com.example.rag.story.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.rag.story.dto.StoryDetailResponse;
import com.example.rag.story.mapper.StoryMapper;

class StoryServiceTest {

    @Test
    void getStoryReturnsStoryWhenStoryExists() {
        StoryMapper storyMapper = mock(StoryMapper.class);
        StoryDetailResponse story = new StoryDetailResponse();
        story.setStoryId(7L);
        story.setTitle("선녀와 나무꾼");
        when(storyMapper.findStoryById(7L)).thenReturn(story);

        StoryService storyService = new StoryService(storyMapper);

        StoryDetailResponse response = storyService.getStory(7L);

        assertThat(response.getStoryId()).isEqualTo(7L);
        assertThat(response.getTitle()).isEqualTo("선녀와 나무꾼");
    }

    @Test
    void getStoryThrowsNotFoundWhenStoryDoesNotExist() {
        StoryMapper storyMapper = mock(StoryMapper.class);
        when(storyMapper.findStoryById(99L)).thenReturn(null);

        StoryService storyService = new StoryService(storyMapper);

        assertThatThrownBy(() -> storyService.getStory(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(responseStatusException.getReason()).contains("storyId=99");
                });
    }
}
