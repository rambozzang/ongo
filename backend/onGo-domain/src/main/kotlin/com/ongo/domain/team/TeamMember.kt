package com.ongo.domain.team

import java.time.LocalDateTime

data class TeamMember(
    val id: Long? = null,
    val userId: Long,
    val memberEmail: String,
    val memberName: String? = null,
    val role: String = "VIEWER",
    val status: String = "INVITED",
    val permissions: String = "{}",
    val invitedAt: LocalDateTime? = null,
    val joinedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
