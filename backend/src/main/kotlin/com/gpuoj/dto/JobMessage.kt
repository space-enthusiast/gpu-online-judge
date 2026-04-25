package com.gpuoj.dto

data class JobMessage(
    val submissionId: String = "",
    val problemId: String = "",
    val sourceCode: String = "",  // base64-encoded
    val testCasesPath: String = "",
    val judgeType: String = "",
    val timeLimitMs: Int = 0,
    val vramLimitMb: Int? = null,
    val speedupThreshold: Double? = null
)
