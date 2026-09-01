package com.ongo.domain.ai

/**
 * 파이프라인 시작 시 차감이 **어디에서 얼마를 가져갔는지** 저장한 스냅샷.
 *
 * ## 왜 저장하는가
 *
 * 파이프라인 정산(사용자 취소, 자연 실패, 재시작 뒤 복구 tick)은 차감이 일어난 요청과
 * **다른 요청**에서 실행된다. 그래서 차감 당시 만든 인메모리 영수증을 가질 수 없다.
 *
 * 영수증이 없으면 환불은 출처를 모른 채 전액을 무료분에 얹는 수밖에 없었다. 그러면
 * 구매분에서 나간 크레딧이 **월말에 사라지는 무료분으로 바뀌고**, `freeMonthly` 한도에
 * 걸린 몫은 그대로 증발한다. 고객이 돈 주고 산 자산이 줄어드는 것이다.
 *
 * 분해를 여기 남겨 두면 정산이 정확히 같은 자리로 되돌릴 수 있다.
 *
 * ## 저장 위치
 *
 * `ai_pipeline_jobs.credit_allocation` (JSONB, nullable). 마이그레이션 이전에 만들어진
 * 행은 `null` 이며, 그 경우 **자동 환불을 하지 않는다** — 임의 무료 환불은 이 클래스가
 * 막으려는 손실을 그대로 만든다.
 *
 * @param freeAmount 무료 크레딧에서 가져간 몫.
 * @param purchasedAmounts `ai_purchased_credits.id` → 그 패키지에서 가져간 몫.
 *   만료일이 다른 패키지 사이에서 유효기간이 바뀌지 않도록 패키지별로 남긴다.
 */
data class PipelineCreditAllocation(
    val freeAmount: Int,
    val purchasedAmounts: Map<Long, Int>,
) {
    /** 이 차감의 총액. 저장된 값과 `total_credits_charged` 는 같아야 한다. */
    val total: Int get() = freeAmount + purchasedAmounts.values.sum()
}
