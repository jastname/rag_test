package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBackendApplication.class, args);
    }
}