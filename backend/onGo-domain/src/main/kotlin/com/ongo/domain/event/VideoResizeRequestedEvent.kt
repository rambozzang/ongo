package com.ongo.domain.event

data class VideoResizeRequestedEvent(
    val resizeId: Long,
    val videoId: Long,
    val userId: Long,
)
