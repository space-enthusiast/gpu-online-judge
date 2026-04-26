package com.gpuoj.repository

import com.gpuoj.model.Submission
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import java.util.UUID

interface SubmissionRepository : ReactiveCrudRepository<Submission, UUID> {
    fun findByUserId(userId: UUID): Flux<Submission>
    fun findByUserIdOrderBySubmittedAtDesc(userId: UUID): Flux<Submission>
    fun findByProblemId(problemId: String): Flux<Submission>
}
