package com.gpuoj.dto

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String
)
