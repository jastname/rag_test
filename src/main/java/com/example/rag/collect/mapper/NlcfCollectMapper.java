package com.example.rag.collect.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.example.rag.collect.dto.KcisaItem;
import com.example.rag.collect.model.CollectHistory;

@Mapper
public interface NlcfCollectMapper {

    Long insertCollectHistory(CollectHistory collectHistory);

    int upsertStory(KcisaItem item);

    int deleteAllEmbeddings();
    
    int deleteAllChunks();
    
    int deleteAllStories();

    int deleteAllCollectHistory();
}