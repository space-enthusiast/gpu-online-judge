package com.gpuoj.controller

import com.gpuoj.dto.AuthResponse
import com.gpuoj.dto.LoginRequest
import com.gpuoj.dto.RegisterRequest
import com.gpuoj.service.AuthService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): Mono<AuthResponse> = authService.register(req)

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): Mono<AuthResponse> = authService.login(req)
}
