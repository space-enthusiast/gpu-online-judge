package com.gpuoj.repository

import com.gpuoj.model.Problem
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux

interface ProblemRepository : ReactiveCrudRepository<Problem, String> {
    @Query("SELECT * FROM problems ORDER BY CASE difficulty WHEN 'EASY' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HARD' THEN 3 ELSE 4 END, title")
    fun findAllOrdered(): Flux<Problem>
}
