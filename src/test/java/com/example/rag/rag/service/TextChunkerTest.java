package com.example.rag.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.rag.common.config.RagProperties;

class TextChunkerTest {

    @Test
    void chunkUsesConfiguredSizeAndOverlap() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setChunkSize(100);
        ragProperties.setChunkOverlap(80);
        TextChunker textChunker = new TextChunker(ragProperties);

        String text = "a".repeat(210);

        List<String> chunks = textChunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).hasSize(100);
        assertThat(chunks.get(1)).hasSize(100);
        assertThat(chunks.get(0).substring(20)).isEqualTo(chunks.get(1).substring(0, 80));
        assertThat(chunks.get(chunks.size() - 1).length()).isLessThanOrEqualTo(100);
    }

    @Test
    void chunkReturnsEmptyForBlankText() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setChunkSize(100);
        ragProperties.setChunkOverlap(80);
        TextChunker textChunker = new TextChunker(ragProperties);

        assertThat(textChunker.chunk("   \n\t  ")).isEmpty();
    }
}