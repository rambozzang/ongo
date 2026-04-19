package com.ongo.domain.channelaudit

import java.time.LocalDateTime

data class ChannelAuditReport(
    val id: Long? = null,
    val userId: Long,
    val overallScore: Int,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
