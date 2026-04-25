package com.gpuoj.dto

data class ResultMessage(
    val submissionId: String = "",
    val verdict: String? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val wallTimeMs: Int? = null,
    val peakVramMb: Int? = null,
    val speedup: Double? = null,
    val compileError: String? = null
)
