package com.example.rag.rag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/rag")
public class RagViewController {

    private static final int DEFAULT_TOP_K = 5;

    @GetMapping("/test")
    public String testPage(Model model) {
        model.addAttribute("topK", DEFAULT_TOP_K);
        return "rag-test";
    }
}