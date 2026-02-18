package com.ongo.application.video

data class ThumbnailGenerationEvent(
    val videoId: Long,
    val inputFilePath: String,
    val durationMs: Long?,
)
