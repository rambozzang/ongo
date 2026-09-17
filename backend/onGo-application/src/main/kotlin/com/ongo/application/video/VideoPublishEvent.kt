package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.enums.MediaType
import java.time.LocalDateTime

data class VideoPublishEvent(
    val videoId: Long,
    val userId: Long,
    val fileUrl: String?,  // null = streaming upload (no permanent file)
    val platformConfigs: List<PlatformUploadConfig>,
)

data class PlatformUploadConfig(
    val platform: Platform,
    val videoUploadId: Long,
    /** 실제 게시 대상 채널. null은 기존 단일 플랫폼 계정 데이터의 하위 호환이다. */
    val channelId: Long? = null,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val visibility: Visibility,
    val thumbnailUrl: String?,
    val customSettingsJson: String? = null,
    val fileSize: Long = 0,
    val scheduledAt: LocalDateTime?,
    /** 예약 디스패처가 외부 호출 전에 확보한 lease. 일반 게시 이벤트는 null이다. */
    val leaseOwner: String? = null,
    /** The source media kind must survive retries and scheduled dispatch. */
    val mediaType: MediaType = MediaType.VIDEO,
)

/**
 * 게시 시도의 **최종 결과**.
 *
 * ## 왜 Boolean 이 아닌가
 *
 * 결과는 셋이다 — 게시됨 / 실패함 / **확인하지 못함**. `VideoUploadPoller` 는 플랫폼
 * 응답을 끝내 확인하지 못했을 때 상태를 `UNCONFIRMED` 로 남긴다. 성공으로 단정하면
 * 없는 게시물을 있다고 말하기 때문이다.
 *
 * 그런데 그 뒤가 `Boolean` 이라 UNCONFIRMED 가 곧바로 "실패" 로 접혔다. 결과는 반대
 * 방향의 거짓말이었다.
 *
 *  - 사용자는 "업로드 실패" 알림을 보고 **다시 올린다** → 플랫폼에 중복 게시물
 *  - UGC 캠페인 제출이 `markPublishFailed()` 로 확정된다 → **유료 파일럿에서 정산 누락**
 *  - 쇼츠 게시가 FAILED 로 기록된다
 *
 * 유료 UGC 에서는 "거짓 실패" 가 "거짓 성공" 보다 비싸다. 실제로 게시된 콘텐츠에
 * 대금을 주지 않는 것이기 때문이다.
 */
enum class UploadOutcome {
    /** 플랫폼이 게시를 확인해 줬다. */
    PUBLISHED,

    /** 플랫폼이 거절했거나 오류를 돌려줬다. **게시물이 없다는 것을 안다.** */
    FAILED,

    /**
     * 결과를 확인하지 못했다. **게시됐을 수도, 안 됐을 수도 있다.**
     *
     * 실패로 취급하면 안 된다 — 사용자가 다시 올려 중복이 생기고, 정산이 누락된다.
     */
    UNCONFIRMED,

    ;

    /** 게시를 **확인했는가**. 성공 경로만 참이다. */
    val isPublished: Boolean get() = this == PUBLISHED

    /** 게시물이 없다고 **단정할 수 있는가**. UNCONFIRMED 는 여기 들어오지 않는다. */
    val isDefiniteFailure: Boolean get() = this == FAILED
}

data class UploadCompletedEvent(
    val videoId: Long,
    val userId: Long,
    val platform: Platform,
    val outcome: UploadOutcome,
    val platformUrl: String? = null,
    /** Provider post identifier, when the provider returned one. */
    val platformPostId: String? = null,
    val errorMessage: String? = null,
    /** The durable upload row that owns this provider result, when known. */
    val videoUploadId: Long? = null,
) {
    /**
     * 게시가 **확인됐는가**. 예전 `success` 필드를 대신한다.
     *
     * 파생 속성으로 둬서 [outcome] 과 어긋날 수 없게 한다. 두 필드를 나란히 두면
     * 한쪽만 고치는 날이 온다.
     *
     * **`!success` 를 "실패" 로 읽지 말 것.** UNCONFIRMED 도 여기서는 false 다.
     * 실패 여부는 [UploadOutcome.isDefiniteFailure] 로 판단한다.
     */
    val success: Boolean get() = outcome.isPublished
}
