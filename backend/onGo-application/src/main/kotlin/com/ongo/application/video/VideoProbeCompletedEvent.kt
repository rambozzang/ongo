package com.ongo.application.video

import com.ongo.domain.video.entity.VideoMediaInfo

data class VideoProbeCompletedEvent(
    val videoId: Long,
    val mediaInfo: VideoMediaInfo,
)
