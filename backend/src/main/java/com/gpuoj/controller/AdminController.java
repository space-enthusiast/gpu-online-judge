package com.gpuoj.controller;

import com.gpuoj.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProblemService problemService;

    @PostMapping("/problems/sync")
    public Mono<String> syncProblems() {
        return problemService.syncProblems();
    }
}
