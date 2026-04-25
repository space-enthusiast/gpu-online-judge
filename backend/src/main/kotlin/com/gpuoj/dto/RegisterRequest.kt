package com.gpuoj.dto

data class RegisterRequest(
    val username: String = "",
    val email: String = "",
    val password: String = ""
)
