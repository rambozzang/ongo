package com.ongo.domain.translation

import java.time.LocalDateTime

data class VideoTranslation(
    val id: Long? = null,
    val videoId: Long,
    val language: String,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = "[]",
    val subtitleContent: String? = null,
    val status: String = "PENDING",
    /**
     * 이 **언어 한 건**의 차감 출처 분해. `null` 이면 자동 환불하지 않는다.
     *
     * 요청 단위 영수증을 언어별로 쪼개 각 행이 자기 몫만 소유한다. 전체를 모든 행에
     * 복사하면 여러 행이 같은 몫을 환불해 없던 크레딧이 생긴다.
     *
     * `null` 은 V109 이전 행뿐이다. 출처를 모르는 채 무료분으로 돌려주면 구매분이
     * 소실되므로 그 경로는 fail-closed 로 막고 수기 정산으로 넘긴다.
     */
    val creditAllocation: TranslationCreditAllocation? = null,
    /** 워커가 집은 시각. `null` 은 미점유. 원자적 claim 과 stale 판정에 쓴다. */
    val claimedAt: LocalDateTime? = null,
    /** 실행 시도 횟수. 상한을 넘으면 재실행 대신 환불하고 끝낸다. */
    val attempts: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

/**
 * 번역 한 건의 차감 출처.
 *
 * @param purchasedAmounts `ai_purchased_credits.id` → 그 패키지에서 가져간 몫.
 *   만료일이 다른 패키지 사이에서 유효기간이 바뀌지 않도록 패키지별로 남긴다.
 */
data class TranslationCreditAllocation(
    /**
     * 환불 대상 사용자. **스냅샷이 스스로 들고 있어야 한다.**
     *
     * 복구 경로는 원본 영상이 삭제된 뒤에도 실행되는데, 그때 `videos.user_id` 로는
     * 소유자를 되짚을 수 없다. 여기 없으면 호출부가 다른 값을 userId 자리에 넘기는 사고가
     * 나고(실제로 `videoId` 를 넘기고 있었다), **남의 계정으로 환불**된다.
     */
    val userId: Long,
    val freeAmount: Int,
    val purchasedAmounts: Map<Long, Int>,
) {
    val total: Int get() = freeAmount + purchasedAmounts.values.sum()
}
