package com.gpuoj.repository

import com.gpuoj.model.Problem
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface ProblemRepository : ReactiveCrudRepository<Problem, String>
