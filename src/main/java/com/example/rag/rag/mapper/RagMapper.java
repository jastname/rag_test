package com.example.rag.rag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rag.rag.model.RagStorySource;
import com.example.rag.rag.model.StoryChunk;
import com.example.rag.rag.model.StoryVector;

@Mapper
public interface RagMapper {

    List<RagStorySource> findAllStoriesForRag();

    int deleteAllStoryVectors();

    int deleteAllStoryChunks();

    int insertStoryChunk(StoryChunk storyChunk);

    int insertStoryVector(StoryVector storyVector);

    List<StoryChunk> findAllChunksWithVectors();

    List<StoryChunk> findChunksByIds(@Param("chunkIds") List<Long> chunkIds);
}
