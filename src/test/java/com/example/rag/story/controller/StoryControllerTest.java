package com.example.rag.story.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.example.rag.common.controller.ApiExceptionHandler;
import com.example.rag.story.dto.StoryDetailResponse;
import com.example.rag.story.service.StoryService;

@WebMvcTest(StoryController.class)
@Import(ApiExceptionHandler.class)
class StoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoryService storyService;

    @Test
    void getStoryReturnsStoryDetail() throws Exception {
        StoryDetailResponse story = new StoryDetailResponse();
        story.setStoryId(1L);
        story.setTitle("해와 달이 된 오누이");
        story.setDescription("옛날 옛적 이야기");
        story.setUseYn("Y");
        when(storyService.getStory(1L)).thenReturn(story);

        mockMvc.perform(get("/api/stories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(1))
                .andExpect(jsonPath("$.title").value("해와 달이 된 오누이"))
                .andExpect(jsonPath("$.description").value("옛날 옛적 이야기"))
                .andExpect(jsonPath("$.useYn").value("Y"));
    }

    @Test
    void getStoryReturnsNotFoundWhenStoryDoesNotExist() throws Exception {
        when(storyService.getStory(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "스토리를 찾을 수 없습니다. storyId=999"));

        mockMvc.perform(get("/api/stories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("스토리를 찾을 수 없습니다. storyId=999"));
    }

    @Test
    void getStoryReturnsBadRequestWhenStoryIdIsNotPositive() throws Exception {
        mockMvc.perform(get("/api/stories/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }
}