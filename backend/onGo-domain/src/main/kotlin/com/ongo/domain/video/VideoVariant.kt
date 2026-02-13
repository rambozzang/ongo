package com.ongo.domain.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.VariantStatus
import java.time.LocalDateTime

data class VideoVariant(
    val id: Long? = null,
    val videoId: Long,
    val platform: Platform,
    val fileUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrateKbps: Int? = null,
    val status: VariantStatus = VariantStatus.PENDING,
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
