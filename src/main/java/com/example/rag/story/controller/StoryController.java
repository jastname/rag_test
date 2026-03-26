package com.example.rag.story.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.rag.story.dto.StoryDetailResponse;
import com.example.rag.story.service.StoryService;

@Validated
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping("/{storyId}")
    public StoryDetailResponse getStory(@PathVariable("storyId") @Positive Long storyId) {
        return storyService.getStory(storyId);
    }
}
