package com.ongo.api.ugc.dto

import com.ongo.domain.ugc.shorts.ShortsRenderJob

/**
 * 렌더 작업 상태. 화면이 3초 간격으로 폴링한다.
 *
 * [failureReason] 은 사용자에게 그대로 보인다. 경로나 스택트레이스를 담지 않는다.
 */
data class ShortsRenderJobResponse(
    val renderJobId: String,
    val status: String,
    val progress: Int? = null,
    val videoId: Long? = null,
    val failureReason: String? = null,
    val attemptCount: Int = 0,
) {
    companion object {
        fun from(job: ShortsRenderJob) = ShortsRenderJobResponse(
            renderJobId = job.id,
            status = job.status.name,
            progress = job.progress,
            videoId = job.videoId,
            failureReason = job.failureReason,
            attemptCount = job.attemptCount,
        )
    }
}

/** 인코더 가용성. URL 임포트 가용성과 같은 형태를 쓴다. */
data class ShortsRenderAvailabilityResponse(
    val available: Boolean,
    val reason: String? = null,
)
