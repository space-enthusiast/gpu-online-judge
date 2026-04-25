package com.gpuoj.controller

import com.gpuoj.service.ProblemService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/admin")
class AdminController(private val problemService: ProblemService) {

    @PostMapping("/problems/sync")
    fun syncProblems(): Mono<String> = problemService.syncProblems()
}
