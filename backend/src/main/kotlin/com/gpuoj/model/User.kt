package com.gpuoj.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("users")
data class User(
    @Id var id: UUID? = null,
    var username: String? = null,
    var email: String? = null,
    var passwordHash: String? = null,
    var role: String = "USER",
    var createdAt: OffsetDateTime? = null
)
