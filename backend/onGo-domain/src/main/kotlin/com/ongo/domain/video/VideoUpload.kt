package com.ongo.domain.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime

data class VideoUpload(
    val id: Long? = null,
    val videoId: Long,
    val platform: Platform,
    /** 연결된 사용자 채널. 기존 데이터는 null이며 플랫폼 기본 채널로 하위 호환한다. */
    val channelId: Long? = null,
    val platformVideoId: String? = null,
    val status: UploadStatus = UploadStatus.UPLOADING,
    val errorMessage: String? = null,
    val platformUrl: String? = null,
    val attemptCount: Int = 0,
    val nextRetryAt: LocalDateTime? = null,
    val leaseOwner: String? = null,
    val leaseUntil: LocalDateTime? = null,
    val pollToken: String? = null,
    val lastError: String? = null,
    val scheduledAt: LocalDateTime? = null,
    val publishedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
