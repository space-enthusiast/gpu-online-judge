package com.gpuoj.controller;

import com.gpuoj.model.Problem;
import com.gpuoj.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Flux<Problem> listProblems() {
        return problemService.listProblems();
    }

    @GetMapping("/{id}")
    public Mono<Problem> getProblem(@PathVariable String id) {
        return problemService.getProblem(id);
    }
}
