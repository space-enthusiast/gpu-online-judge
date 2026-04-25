package com.gpuoj.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("submissions")
data class Submission(
    @Id var id: UUID? = null,
    var userId: UUID? = null,
    var problemId: String? = null,
    var sourceCode: String? = null,
    var language: String = "CUDA",
    var status: String = "PENDING",
    var verdict: String? = null,
    var wallTimeMs: Int? = null,
    var peakVramMb: Int? = null,
    var speedup: Double? = null,
    var stdout: String? = null,
    var stderr: String? = null,
    var submittedAt: OffsetDateTime? = null
)
