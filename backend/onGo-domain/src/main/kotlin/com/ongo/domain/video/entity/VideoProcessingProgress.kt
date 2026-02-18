package com.ongo.domain.video.entity

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

enum class ProcessingStage {
    PROBE, THUMBNAIL, CAPTION, UPLOAD
}

data class VideoProcessingProgress(
    val id: Long? = null,
    val videoId: Long,
    val stage: ProcessingStage,
    val platform: Platform? = null,
    val progressPercent: Int = 0,
    val message: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
