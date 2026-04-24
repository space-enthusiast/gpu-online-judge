package com.gpuoj.controller;

import com.gpuoj.dto.AuthResponse;
import com.gpuoj.dto.LoginRequest;
import com.gpuoj.dto.RegisterRequest;
import com.gpuoj.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Mono<AuthResponse> register(@RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
