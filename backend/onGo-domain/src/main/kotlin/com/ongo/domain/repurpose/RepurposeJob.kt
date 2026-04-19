package com.ongo.domain.repurpose

import java.time.LocalDateTime

data class RepurposeJob(
    val id: Long = 0,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val status: String = "PENDING",
    val clipCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
