package com.ongo.application.admin

import com.ongo.application.admin.dto.AdminRenewalReviewItem
import com.ongo.application.admin.dto.AdminRenewalReviewRecheckResult
import com.ongo.application.subscription.RenewalReviewDecision
import com.ongo.application.subscription.RenewalReviewRecheck
import com.ongo.application.subscription.SubscriptionRenewalService
import com.ongo.common.config.PageResponse
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 사람이 확인해야 하는 갱신 주기를 **보이게** 만든다.
 *
 * `NEEDS_REVIEW` 는 "코드가 정할 수 없으니 사람이 보라" 는 뜻인데, 볼 화면도 조회 경로도
 * 없으면 그 원장은 없는 것과 같았다. 결제 내역에는 끝나지 않는 PENDING 이 남고, 브라우저
 * 재조회는 자동 갱신 결제를 명시적으로 제외한다.
 *
 * 이 유스케이스는 **조회와 재조회 트리거만** 한다. 확정 결과를 인자로 받지 않는다.
 */
@Service
class AdminSubscriptionReviewUseCase(
    private val renewalAttemptRepository: SubscriptionRenewalAttemptRepository,
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val renewalService: SubscriptionRenewalService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<AdminRenewalReviewItem> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val attempts = renewalAttemptRepository.findByOutcome(
            SubscriptionRenewalOutcome.NEEDS_REVIEW,
            limit = safeSize,
            offset = safePage * safeSize,
        )
        val total = renewalAttemptRepository.countByOutcome(SubscriptionRenewalOutcome.NEEDS_REVIEW)
        return PageResponse.of(attempts.map(::toItem), safePage, safeSize, total)
    }

    /**
     * 재조회를 트리거한다. 결과는 PG 가 정한다.
     *
     * 조회 실패를 성공/실패 어느 쪽으로도 번역하지 않는다. PG 원문 오류는 로그에만 남기고
     * 응답에는 안전한 문장만 넣는다 — 관리자 화면이라도 외부 시스템 오류 원문을 그대로
     * 흘리면 내부 구조가 드러난다.
     */
    fun recheck(attemptId: Long): AdminRenewalReviewRecheckResult {
        val result = try {
            renewalService.recheckReview(attemptId, LocalDateTime.now())
        } catch (e: Exception) {
            log.error("갱신 확인 대상 재조회 실패. attemptId={}", attemptId, e)
            RenewalReviewRecheck(
                RenewalReviewDecision.LOOKUP_FAILED,
                "결제 상태를 조회하지 못했습니다. 상태는 그대로 두었습니다. 잠시 후 다시 시도해 주세요.",
            )
        }
        return AdminRenewalReviewRecheckResult(
            attemptId = attemptId,
            decision = result.decision.name,
            changed = result.changed,
            outcome = result.outcome?.name,
            reason = result.reason,
        )
    }

    /**
     * 원인은 저장돼 있지 않다. `payment_id` 유무로만 갈린다.
     *
     * 금액 불일치인지 통화 불일치인지는 PG 를 다시 물어야 알 수 있고, 목록 조회가 매 행마다
     * 외부 호출을 하면 안 된다. 그래서 목록에서는 **자동 확정이 가능한 종류인지**만 알려주고,
     * 정확한 사유는 재조회 응답이 준다.
     */
    private fun toItem(attempt: SubscriptionRenewalAttempt): AdminRenewalReviewItem {
        val payment = attempt.paymentId?.let(paymentRepository::findById)
        val subscription = subscriptionRepository.findById(attempt.subscriptionId)
        val legacy = attempt.paymentId == null
        return AdminRenewalReviewItem(
            attemptId = attempt.id,
            subscriptionId = attempt.subscriptionId,
            periodStart = attempt.periodStart,
            outcome = attempt.outcome.name,
            createdAt = attempt.createdAt,
            cause = if (legacy) CAUSE_LEGACY else CAUSE_APPROVAL_MISMATCH,
            reason = if (legacy) {
                "내부 결제 원장이 없는 과거 주기입니다. 자동 확정 대상이 아니며 수기 대사가 필요합니다."
            } else {
                "PG 승인 내용이 내부 결제와 달라 확정하지 못했습니다. 재조회로 확인해 주세요."
            },
            paymentId = attempt.paymentId,
            externalPaymentId = attempt.paymentId?.let { "ongo-$it" },
            paymentStatus = payment?.status?.name,
            paymentAmount = payment?.amount,
            paymentCurrency = payment?.currency,
            subscriptionStatus = subscription?.status?.name,
            subscriptionPlanType = subscription?.planType?.name,
            subscriptionPrice = subscription?.price,
            subscriptionBillingCycle = subscription?.billingCycle?.name,
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val CAUSE_LEGACY = "LEGACY_NO_INTERNAL_LEDGER"
        const val CAUSE_APPROVAL_MISMATCH = "APPROVAL_MISMATCH"
    }
}
