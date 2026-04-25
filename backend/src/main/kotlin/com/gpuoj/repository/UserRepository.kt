package com.gpuoj.repository

import com.gpuoj.model.User
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.util.UUID

interface UserRepository : ReactiveCrudRepository<User, UUID> {
    fun findByUsername(username: String): Mono<User>
    fun findByEmail(email: String): Mono<User>
    fun existsByUsername(username: String): Mono<Boolean>
    fun existsByEmail(email: String): Mono<Boolean>
}
