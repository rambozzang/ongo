package com.ongo.domain.ugc.shorts

import java.time.Instant

/** 클립의 플랫폼별 게시 위임 결과. */
data class ClipPublication(
    val id: Long = 0,
    val clipId: Long,
    val platform: String,
    val videoUploadId: Long? = null,
    val status: ClipPublicationStatus = ClipPublicationStatus.PENDING,
    val scheduledAt: Instant? = null,
    val publishedAt: Instant? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
