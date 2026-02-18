package com.ongo.application.video

data class CaptionGenerationEvent(
    val videoId: Long,
    val userId: Long,
    val fileUrl: String,
    val language: String = "ko",
)
