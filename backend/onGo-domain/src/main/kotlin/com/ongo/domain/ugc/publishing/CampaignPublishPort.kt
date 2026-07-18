package com.ongo.domain.ugc.publishing

/**
 * 캠페인 모듈이 SNS 게시 세부를 직접 참조하지 않도록 하는 포트.
 * 인프라/어댑터가 기존 멀티 SNS 게시 흐름에 위임한다.
 */
interface CampaignPublishPort {
    /**
     * 크리에이터 소유 영상을 지정 플랫폼에 게시하고 플랫폼별 결과를 반환한다.
     * 게시는 비동기이므로 즉시 상태는 보통 UPLOADING이며, 최종 결과는 이후 동기화된다.
     */
    fun publish(creatorId: Long, videoId: Long, platforms: List<String>): List<PlatformPublishOutcome>
}

data class PlatformPublishOutcome(
    val platform: String,
    val videoUploadId: Long?,
    val status: String,
    val errorMessage: String? = null,
)
