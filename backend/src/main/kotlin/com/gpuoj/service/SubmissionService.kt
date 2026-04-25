package com.gpuoj.service

import com.gpuoj.dto.JobMessage
import com.gpuoj.dto.ResultMessage
import com.gpuoj.dto.SubmissionRequest
import com.gpuoj.model.Submission
import com.gpuoj.repository.ProblemRepository
import com.gpuoj.repository.SubmissionRepository
import com.gpuoj.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

@Service
class SubmissionService(
    private val submissionRepo: SubmissionRepository,
    private val problemRepo: ProblemRepository,
    private val userRepo: UserRepository,
    private val kafkaPipeline: KafkaPipeline
) {
    @Value("\${app.problems.local-path}")
    private lateinit var localProblemsPath: String

    fun submit(req: SubmissionRequest): Mono<Submission> {
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication.name }
            .flatMap { username -> userRepo.findByUsername(username) }
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED)))
            .flatMap { user ->
                problemRepo.findById(req.problemId)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found")))
                    .flatMap { problem ->
                        val sub = Submission(
                            userId = user.id,
                            problemId = problem.id,
                            sourceCode = req.sourceCode,
                            status = "PENDING",
                            submittedAt = OffsetDateTime.now()
                        )
                        submissionRepo.save(sub).flatMap { saved ->
                            kafkaPipeline.registerPending(saved.id!!)
                            val job = JobMessage(
                                submissionId = saved.id.toString(),
                                problemId = problem.id!!,
                                sourceCode = Base64.getEncoder().encodeToString(req.sourceCode.toByteArray()),
                                testCasesPath = "$localProblemsPath/${problem.id}/tests/",
                                judgeType = problem.judgeType ?: "",
                                timeLimitMs = problem.timeLimitMs,
                                vramLimitMb = problem.vramLimitMb,
                                speedupThreshold = problem.speedupThreshold
                            )
                            kafkaPipeline.publishJob(job).thenReturn(saved)
                        }
                    }
            }
    }

    fun getSubmission(id: UUID): Mono<Submission> =
        submissionRepo.findById(id)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))

    fun streamStatus(submissionId: UUID): Flux<ServerSentEvent<ResultMessage>> {
        return submissionRepo.findById(submissionId)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMapMany { sub ->
                if (sub.status != "PENDING" && sub.status != "JUDGING") {
                    val result = ResultMessage(
                        submissionId = submissionId.toString(),
                        verdict = sub.verdict,
                        stdout = sub.stdout,
                        stderr = sub.stderr,
                        wallTimeMs = sub.wallTimeMs,
                        peakVramMb = sub.peakVramMb,
                        speedup = sub.speedup
                    )
                    return@flatMapMany Flux.just(ServerSentEvent.builder(result).build())
                }

                val sink: Sinks.One<ResultMessage> = kafkaPipeline.registerPending(submissionId)
                sink.asMono()
                    .flatMap { result ->
                        updateSubmissionFromResult(submissionId, result).thenReturn(result)
                    }
                    .map { result -> ServerSentEvent.builder(result).build() }
                    .flux()
                    .timeout(Duration.ofMinutes(10))
                    .doFinally { kafkaPipeline.removePending(submissionId) }
            }
    }

    private fun updateSubmissionFromResult(id: UUID, result: ResultMessage): Mono<Submission> {
        return submissionRepo.findById(id).flatMap { sub ->
            sub.verdict = result.verdict
            sub.status = result.verdict ?: sub.status
            sub.stdout = result.stdout
            sub.stderr = result.stderr
            sub.wallTimeMs = result.wallTimeMs
            sub.peakVramMb = result.peakVramMb
            sub.speedup = result.speedup
            submissionRepo.save(sub)
        }
    }
}
