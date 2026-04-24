package com.gpuoj.service;

import com.gpuoj.dto.JobMessage;
import com.gpuoj.dto.ResultMessage;
import com.gpuoj.dto.SubmissionRequest;
import com.gpuoj.model.Submission;
import com.gpuoj.repository.ProblemRepository;
import com.gpuoj.repository.SubmissionRepository;
import com.gpuoj.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepo;
    private final ProblemRepository problemRepo;
    private final UserRepository userRepo;
    private final KafkaPipeline kafkaPipeline;

    @Value("${app.problems.local-path}")
    private String localProblemsPath;

    public Mono<Submission> submit(SubmissionRequest req) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(username -> userRepo.findByUsername(username))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(user -> problemRepo.findById(req.getProblemId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found")))
                        .flatMap(problem -> {
                            Submission sub = new Submission();
                            sub.setUserId(user.getId());
                            sub.setProblemId(problem.getId());
                            sub.setSourceCode(req.getSourceCode());
                            sub.setStatus("PENDING");
                            sub.setSubmittedAt(OffsetDateTime.now());
                            return submissionRepo.save(sub)
                                    .flatMap(saved -> {
                                        // Register SSE sink before publishing to Kafka
                                        kafkaPipeline.registerPending(saved.getId());

                                        JobMessage job = JobMessage.builder()
                                                .submissionId(saved.getId().toString())
                                                .problemId(problem.getId())
                                                .sourceCode(Base64.getEncoder().encodeToString(
                                                        req.getSourceCode().getBytes()))
                                                .testCasesPath(localProblemsPath + "/" + problem.getId() + "/tests/")
                                                .judgeType(problem.getJudgeType())
                                                .timeLimitMs(problem.getTimeLimitMs())
                                                .vramLimitMb(problem.getVramLimitMb())
                                                .speedupThreshold(problem.getSpeedupThreshold())
                                                .build();

                                        return kafkaPipeline.publishJob(job)
                                                .thenReturn(saved);
                                    });
                        }));
    }

    public Mono<Submission> getSubmission(UUID id) {
        return submissionRepo.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Flux<ServerSentEvent<ResultMessage>> streamStatus(UUID submissionId) {
        return submissionRepo.findById(submissionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMapMany(sub -> {
                    // Already judged
                    if (!sub.getStatus().equals("PENDING") && !sub.getStatus().equals("JUDGING")) {
                        ResultMessage result = ResultMessage.builder()
                                .submissionId(submissionId.toString())
                                .verdict(sub.getVerdict())
                                .stdout(sub.getStdout())
                                .stderr(sub.getStderr())
                                .wallTimeMs(sub.getWallTimeMs())
                                .peakVramMb(sub.getPeakVramMb())
                                .speedup(sub.getSpeedup())
                                .build();
                        return Flux.just(ServerSentEvent.builder(result).build());
                    }

                    Sinks.One<ResultMessage> sink = kafkaPipeline.registerPending(submissionId);
                    return sink.asMono()
                            .flatMap(result -> updateSubmissionFromResult(submissionId, result)
                                    .thenReturn(result))
                            .map(result -> ServerSentEvent.builder(result).build())
                            .flux()
                            .timeout(Duration.ofMinutes(10))
                            .doFinally(sig -> kafkaPipeline.removePending(submissionId));
                });
    }

    private Mono<Submission> updateSubmissionFromResult(UUID id, ResultMessage result) {
        return submissionRepo.findById(id)
                .flatMap(sub -> {
                    sub.setVerdict(result.getVerdict());
                    sub.setStatus(result.getVerdict());
                    sub.setStdout(result.getStdout());
                    sub.setStderr(result.getStderr());
                    sub.setWallTimeMs(result.getWallTimeMs());
                    sub.setPeakVramMb(result.getPeakVramMb());
                    sub.setSpeedup(result.getSpeedup());
                    return submissionRepo.save(sub);
                });
    }
}
