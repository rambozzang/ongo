package com.ongo.application.subscription

import com.ongo.domain.subscription.SubscriptionRenewalOutcome

/**
 * 운영자 재확인의 결말.
 *
 * "바뀌지 않았다" 를 성공과 구분해서 돌려주는 것이 이 타입의 목적이다. 재확인은 대부분
 * 아무것도 바꾸지 않는 것이 정답이고, 그것을 실패로 표현하면 운영자가 다시 누르게 된다.
 */
enum class RenewalReviewDecision {
    /** PG 재조회 결과로 확정했다. */
    RESOLVED,

    /** 다른 요청이 먼저 확정했다. 오류가 아니다. */
    ALREADY_RESOLVED,

    /** 확인 대상이 아니다(이미 다른 결과). */
    NOT_UNDER_REVIEW,

    /** PG 가 아직 결말을 내지 않았거나 금액·통화가 달라 자동으로 정할 수 없다. */
    STILL_UNDER_REVIEW,

    /** 내부 원장이 없어 코드가 확정할 수 없다. 수기 대사 대상이다. */
    MANUAL_ONLY,

    /**
     * PG 조회 자체가 실패했다. **아무것도 바뀌지 않았다.**
     *
     * 장애를 "결제 없음" 으로 읽으면 승인된 결제를 미결제로 확정한다. 모른다는 사실을
     * 모른다고 돌려주고 다시 시도하게 한다.
     */
    LOOKUP_FAILED,

    NOT_FOUND,
}

/**
 * @param reason 운영자에게 그대로 보여줄 문장. 내부 식별자·PG 원문 오류를 넣지 않는다.
 * @param outcome 확정했거나 이미 확정돼 있던 결과. 바뀌지 않았으면 null.
 */
data class RenewalReviewRecheck(
    val decision: RenewalReviewDecision,
    val reason: String,
    val outcome: SubscriptionRenewalOutcome? = null,
) {
    val changed: Boolean get() = decision == RenewalReviewDecision.RESOLVED
}
