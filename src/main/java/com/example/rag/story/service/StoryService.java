package com.example.rag.story.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.rag.story.dto.StoryDetailResponse;
import com.example.rag.story.mapper.StoryMapper;

@Service
public class StoryService {

    private final StoryMapper storyMapper;

    public StoryService(StoryMapper storyMapper) {
        this.storyMapper = storyMapper;
    }

    public StoryDetailResponse getStory(Long storyId) {
        StoryDetailResponse story = storyMapper.findStoryById(storyId);
        if (story == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "스토리를 찾을 수 없습니다. storyId=" + storyId);
        }
        return story;
    }
}
