package com.example.rag.rag.dto;

public record OllamaGenerateRequest(String model, String prompt, boolean stream) {
}
