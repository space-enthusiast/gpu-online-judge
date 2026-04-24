package com.gpuoj.controller;

import com.gpuoj.dto.ResultMessage;
import com.gpuoj.dto.SubmissionRequest;
import com.gpuoj.model.Submission;
import com.gpuoj.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public Mono<Submission> submit(@RequestBody SubmissionRequest req) {
        return submissionService.submit(req);
    }

    @GetMapping("/{id}")
    public Mono<Submission> getSubmission(@PathVariable UUID id) {
        return submissionService.getSubmission(id);
    }

    @GetMapping(value = "/{id}/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ResultMessage>> streamStatus(@PathVariable UUID id) {
        return submissionService.streamStatus(id);
    }
}
