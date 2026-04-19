package com.ongo.domain.repurpose

import java.time.LocalDateTime

data class RepurposeClip(
    val id: Long = 0,
    val jobId: Long,
    val startTime: String,
    val endTime: String,
    val title: String,
    val description: String,
    val viralScore: Int,
    val reasoning: String,
    val suggestedPlatform: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
