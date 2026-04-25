package com.gpuoj.service

import com.gpuoj.dto.AuthResponse
import com.gpuoj.dto.LoginRequest
import com.gpuoj.dto.RegisterRequest
import com.gpuoj.model.User
import com.gpuoj.repository.UserRepository
import com.gpuoj.security.JwtUtil
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class AuthService(
    private val userRepo: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {

    fun register(req: RegisterRequest): Mono<AuthResponse> {
        return userRepo.existsByUsername(req.username)
            .flatMap { exists ->
                if (exists) Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "Username taken"))
                else userRepo.existsByEmail(req.email)
            }
            .flatMap { emailExists ->
                if (emailExists) Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "Email taken"))
                else userRepo.save(
                    User(
                        username = req.username,
                        email = req.email,
                        passwordHash = passwordEncoder.encode(req.password),
                        role = "USER"
                    )
                )
            }
            .map { user ->
                AuthResponse(
                    token = jwtUtil.generate(user.username!!, user.role),
                    username = user.username!!,
                    role = user.role
                )
            }
    }

    fun login(req: LoginRequest): Mono<AuthResponse> {
        return userRepo.findByUsername(req.username)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")))
            .flatMap { user ->
                if (!passwordEncoder.matches(req.password, user.passwordHash)) {
                    Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"))
                } else {
                    Mono.just(
                        AuthResponse(
                            token = jwtUtil.generate(user.username!!, user.role),
                            username = user.username!!,
                            role = user.role
                        )
                    )
                }
            }
    }
}
