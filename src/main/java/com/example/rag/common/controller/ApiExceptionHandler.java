package com.example.rag.common.controller;

import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "message", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason()
                ));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "success", false,
                        "message", "요청 값이 올바르지 않습니다."
                ));
    }
}
