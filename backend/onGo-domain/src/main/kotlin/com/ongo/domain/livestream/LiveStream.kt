package com.ongo.domain.livestream

import java.time.LocalDateTime

data class LiveStream(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val platform: String,
    val status: String = "SCHEDULED",
    val scheduledAt: LocalDateTime,
    val startedAt: LocalDateTime? = null,
    val endedAt: LocalDateTime? = null,
    val viewerCount: Int = 0,
    val peakViewers: Int = 0,
    val chatMessages: Int = 0,
    val streamUrl: String? = null,
    val thumbnailUrl: String? = null,
)
