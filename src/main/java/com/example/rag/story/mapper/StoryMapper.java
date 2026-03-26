package com.example.rag.story.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rag.story.dto.StoryDetailResponse;

@Mapper
public interface StoryMapper {

    StoryDetailResponse findStoryById(@Param("storyId") Long storyId);
}
