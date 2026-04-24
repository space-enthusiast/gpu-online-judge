package com.gpuoj.repository;

import com.gpuoj.model.Submission;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SubmissionRepository extends ReactiveCrudRepository<Submission, UUID> {
    Flux<Submission> findByUserId(UUID userId);
    Flux<Submission> findByProblemId(String problemId);
}
