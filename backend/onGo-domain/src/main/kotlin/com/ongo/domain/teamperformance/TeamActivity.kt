package com.ongo.domain.teamperformance

import java.time.LocalDateTime

data class TeamActivity(
    val id: Long = 0,
    val workspaceId: Long,
    val memberId: Long,
    val memberName: String,
    val action: String,
    val target: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
