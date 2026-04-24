package com.gpuoj.repository;

import com.gpuoj.model.Problem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ProblemRepository extends ReactiveCrudRepository<Problem, String> {
}
