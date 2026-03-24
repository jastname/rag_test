package com.example.rag.rag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.rag.common.config.RagProperties;

@Component
public class TextChunker {

    private final RagProperties ragProperties;

    public TextChunker(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<String> chunk(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(100, ragProperties.getChunkSize());
        int overlap = Math.max(0, Math.min(ragProperties.getChunkOverlap(), chunkSize / 2));
        int step = Math.max(1, chunkSize - overlap);

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + chunkSize);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
        }
        return chunks;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
