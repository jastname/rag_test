package com.example.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.rag.rag.controller.RagController;
import com.example.rag.rag.service.RagService;

@SpringBootTest
class RagBackendApplicationTests {

    @Autowired
    private RagController ragController;

    @Autowired
    private RagService ragService;

    @Test
    void contextLoads() {
        assertThat(ragController).isNotNull();
        assertThat(ragService).isNotNull();
    }
}