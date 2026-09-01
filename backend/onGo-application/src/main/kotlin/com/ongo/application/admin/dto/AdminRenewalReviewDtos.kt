package com.ongo.application.admin.dto

import java.time.LocalDateTime

/**
 * 사람이 확인해야 하는 갱신 주기 한 건.
 *
 * ## 여기에 넣지 않는 것
 *
 * 빌링키, 카드 정보, 이메일, PG 시크릿, PG 원문 오류. 운영자가 판단하는 데 필요한 것은
 * "얼마를 어떤 통화로 청구했고 PG 는 뭐라고 했는가" 뿐이고, 결제 수단 자체는 그 판단에
 * 필요하지 않다. 관리자 화면이라도 새면 그 값 하나로 반복 청구가 가능하다.
 */
data class AdminRenewalReviewItem(
    val attemptId: Long,
    val subscriptionId: Long,
    val periodStart: LocalDateTime,
    val outcome: String,
    val createdAt: LocalDateTime,
    /** 자동 확정이 가능한 종류인지. `LEGACY_NO_INTERNAL_LEDGER` 또는 `APPROVAL_MISMATCH`. */
    val cause: String,
    /** 운영자에게 그대로 보여줄 문장. */
    val reason: String,
    val paymentId: Long?,
    /** PortOne 조회에 쓰는 외부 id. 내부 원장이 없으면 null. */
    val externalPaymentId: String?,
    val paymentStatus: String?,
    val paymentAmount: Int?,
    val paymentCurrency: String?,
    val subscriptionStatus: String?,
    val subscriptionPlanType: String?,
    val subscriptionPrice: Int?,
    val subscriptionBillingCycle: String?,
)

/**
 * 재조회 결과.
 *
 * [changed] 가 false 인 것이 정상 결말인 경우가 많다. 실패가 아니라 "바꿀 근거가 없었다"
 * 이므로 [reason] 을 그대로 보여주면 된다.
 */
data class AdminRenewalReviewRecheckResult(
    val attemptId: Long,
    val decision: String,
    val changed: Boolean,
    val outcome: String?,
    val reason: String,
)
