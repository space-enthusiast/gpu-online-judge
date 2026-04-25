package com.gpuoj.controller

import com.gpuoj.dto.ResultMessage
import com.gpuoj.dto.SubmissionRequest
import com.gpuoj.model.Submission
import com.gpuoj.service.SubmissionService
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/submissions")
class SubmissionController(private val submissionService: SubmissionService) {

    @PostMapping
    fun submit(@RequestBody req: SubmissionRequest): Mono<Submission> = submissionService.submit(req)

    @GetMapping("/{id}")
    fun getSubmission(@PathVariable id: UUID): Mono<Submission> = submissionService.getSubmission(id)

    @GetMapping(value = ["/{id}/status"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamStatus(@PathVariable id: UUID): Flux<ServerSentEvent<ResultMessage>> =
        submissionService.streamStatus(id)
}
