package com.ongo.application.video

data class VideoPostProcessEvent(
    val videoId: Long,
    val userId: Long,
    val fileUrl: String,
)
