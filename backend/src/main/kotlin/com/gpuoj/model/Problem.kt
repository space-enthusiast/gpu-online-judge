package com.gpuoj.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("problems")
data class Problem(
    @Id var id: String? = null,
    var title: String? = null,
    var difficulty: String? = null,
    var judgeType: String? = null,
    var timeLimitMs: Int = 0,
    var vramLimitMb: Int? = null,
    var speedupThreshold: Double? = null,
    var gitCommitHash: String? = null,
    var syncedAt: OffsetDateTime? = null
)
