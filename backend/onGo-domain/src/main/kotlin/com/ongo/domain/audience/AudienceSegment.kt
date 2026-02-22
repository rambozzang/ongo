package com.ongo.domain.audience

import java.time.LocalDateTime

data class AudienceSegment(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val description: String? = null,
    val conditions: String = "{}",
    val autoUpdate: Boolean = true,
    val memberCount: Int = 0,
    val createdAt: LocalDateTime? = null,
)
