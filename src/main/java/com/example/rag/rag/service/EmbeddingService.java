package com.example.rag.rag.service;

public interface EmbeddingService {

    double[] embed(String text);

    String modelName();
}
