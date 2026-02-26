package com.ongo.application.livestream.dto

import java.time.LocalDateTime

data class LiveStreamResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val platform: String,
    val status: String,
    val scheduledAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val viewerCount: Int,
    val peakViewers: Int,
    val chatMessages: Int,
    val streamUrl: String?,
    val thumbnailUrl: String?,
)

data class StreamChatResponse(
    val id: Long,
    val streamId: Long,
    val username: String,
    val message: String,
    val timestamp: LocalDateTime?,
    val isHighlighted: Boolean,
    val isModerator: Boolean,
)

data class LiveStreamSummaryResponse(
    val totalStreams: Int,
    val liveNow: Int,
    val avgViewers: Int,
    val totalChatMessages: Long,
    val nextScheduled: LocalDateTime?,
)

data class CreateStreamRequest(
    val title: String,
    val description: String? = null,
    val platform: String,
    val scheduledAt: LocalDateTime,
)
