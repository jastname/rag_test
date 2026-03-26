package com.example.rag.rag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rag.rag.model.RagStorySource;
import com.example.rag.rag.model.StoryChunk;
import com.example.rag.rag.model.StoryVector;

@Mapper
public interface RagMapper {

    // RAG 인덱싱 대상이 되는 원본 스토리 전체를 읽는다.
    // use_yn='Y' 조건은 XML 쿼리에서 걸고, 여기서는 의미상 진입점만 노출한다.
    List<RagStorySource> findAllStoriesForRag();

    // 재색인 전에 기존 임베딩 벡터를 비운다.
    // 청크보다 벡터가 외래키 의존도가 높아 보통 먼저 삭제한다.
    int deleteAllStoryVectors();

    // 재색인 전에 기존 텍스트 청크를 비운다.
    int deleteAllStoryChunks();

    // 청크 단위 검색/임베딩의 기준이 되는 텍스트 레코드를 저장한다.
    int insertStoryChunk(StoryChunk storyChunk);

    // 청크에 대응하는 임베딩 벡터를 저장한다.
    int insertStoryVector(StoryVector storyVector);

    // 제목 완전 일치 검색용 쿼리.
    // 작품명 질의에서 가장 우선순위가 높은 후보를 뽑는 데 사용한다.
    List<StoryChunk> findChunksByExactTitle(@Param("title") String title);

    // 제목 부분 일치 검색용 쿼리.
    // 질문형 꼬리표가 붙은 제목이나 일부 키워드만 들어온 경우를 보강한다.
    List<StoryChunk> findChunksByTitleKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    // 설명(description) 본문 LIKE 검색용 쿼리.
    // 설명형 질의나 확장 키워드 보강 검색에 사용한다.
    List<StoryChunk> findChunksByDescriptionKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    // 벡터 유사도 계산 대상이 되는 후보 청크를 가져온다.
    // 현재는 Java에서 similarity를 계산하므로 원문 벡터까지 함께 읽는다.
    List<StoryChunk> findVectorCandidateChunks(@Param("limit") int limit);

    // 전체 청크+벡터를 한 번에 읽는 레거시/보조 메서드다.
    // 현재 주 검색 경로에서는 직접 사용하지 않지만 테스트나 보조 작업에서 재사용할 수 있다.
    List<StoryChunk> findAllChunksWithVectors();

    // 특정 chunkId 집합만 다시 읽는 보조 조회다.
    List<StoryChunk> findChunksByIds(@Param("chunkIds") List<Long> chunkIds);
}