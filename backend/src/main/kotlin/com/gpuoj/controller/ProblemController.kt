package com.gpuoj.controller

import com.gpuoj.model.Problem
import com.gpuoj.service.ProblemService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/problems")
class ProblemController(private val problemService: ProblemService) {

    @GetMapping
    fun listProblems(): Flux<Problem> = problemService.listProblems()

    @GetMapping("/{id}")
    fun getProblem(@PathVariable id: String): Mono<Problem> = problemService.getProblem(id)
}
