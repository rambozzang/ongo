package com.ongo.application.video

import com.ongo.domain.video.entity.VideoMediaInfo

interface MediaProbeService {
    fun probe(inputFilePath: String, videoId: Long): VideoMediaInfo
}

interface ThumbnailService {
    fun generateThumbnails(inputFilePath: String, videoId: Long, durationMs: Long?): List<String>
}
