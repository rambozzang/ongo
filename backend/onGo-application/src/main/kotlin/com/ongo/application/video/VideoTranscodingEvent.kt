package com.ongo.application.video

import com.ongo.common.enums.Platform

data class VideoTranscodingEvent(
    val videoId: Long,
    val userId: Long,
    val fileUrl: String,
    val platforms: List<Platform>,
)
