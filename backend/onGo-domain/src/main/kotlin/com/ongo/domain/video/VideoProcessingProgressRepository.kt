package com.ongo.domain.video

import com.ongo.common.enums.Platform
import com.ongo.domain.video.entity.ProcessingStage
import com.ongo.domain.video.entity.VideoProcessingProgress

interface VideoProcessingProgressRepository {
    fun save(progress: VideoProcessingProgress): VideoProcessingProgress
    fun upsertProgress(videoId: Long, stage: ProcessingStage, platform: Platform?, progressPercent: Int, message: String?)
    fun findByVideoId(videoId: Long): List<VideoProcessingProgress>
    fun deleteByVideoId(videoId: Long)
}
