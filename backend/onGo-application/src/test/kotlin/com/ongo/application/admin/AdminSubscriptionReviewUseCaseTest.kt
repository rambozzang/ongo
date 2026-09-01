package com.ongo.application.admin

import com.ongo.application.admin.dto.AdminRenewalReviewItem
import com.ongo.application.subscription.RenewalReviewDecision
import com.ongo.application.subscription.RenewalReviewRecheck
import com.ongo.application.subscription.SubscriptionRenewalService
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 운영자 화면의 계약.
 *
 * 여기서 지키는 것은 둘이다.
 *
 *  1. **확인 대상만 보인다** — 다른 결과가 섞이면 목록이 작업 대기열이 아니게 된다
 *  2. **결제 수단은 절대 나가지 않는다** — 관리자 화면이라도 빌링키가 새면 그 값 하나로
 *     반복 청구가 가능하다
 */
class AdminSubscriptionReviewUseCaseTest {

    private val attemptRepository = mockk<SubscriptionRenewalAttemptRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val renewalService = mockk<SubscriptionRenewalService>()

    private val useCase = AdminSubscriptionReviewUseCase(
        renewalAttemptRepository = attemptRepository,
        paymentRepository = paymentRepository,
        subscriptionRepository = subscriptionRepository,
        renewalService = renewalService,
    )

    private fun attempt(id: Long, paymentId: Long?) = SubscriptionRenewalAttempt(
        id = id,
        subscriptionId = 5L,
        periodStart = LocalDateTime.parse("2026-09-01T00:00:00"),
        outcome = SubscriptionRenewalOutcome.NEEDS_REVIEW,
        paymentId = paymentId,
        createdAt = LocalDateTime.parse("2026-09-01T02:00:00"),
    )

    private fun payment() = Payment(
        id = 4242L,
        userId = 11L,
        type = PaymentType.SUBSCRIPTION,
        amount = 19_900,
        currency = "KRW",
        status = PaymentStatus.PENDING,
        pgProvider = "portone",
        description = "SUBSCRIPTION_RENEWAL|PRO|MONTHLY",
    )

    private fun subscription() = Subscription(
        id = 5L,
        userId = 11L,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = 19_900,
        billingCycle = BillingCycle.MONTHLY,
        billingKeyEncrypted = "enc-billing-key-should-never-leave",
    )

    private fun givenList(vararg attempts: SubscriptionRenewalAttempt) {
        every {
            attemptRepository.findByOutcome(SubscriptionRenewalOutcome.NEEDS_REVIEW, any(), any())
        } returns attempts.toList()
        every { attemptRepository.countByOutcome(SubscriptionRenewalOutcome.NEEDS_REVIEW) } returns attempts.size.toLong()
        every { paymentRepository.findById(4242L) } returns payment()
        every { subscriptionRepository.findById(5L) } returns subscription()
    }

    /* ---- 목록 ---- */

    @Test
    fun `확인 대상만 조회한다`() {
        givenList(attempt(1L, 4242L))

        useCase.list(page = 0, size = 20)

        verify { attemptRepository.findByOutcome(SubscriptionRenewalOutcome.NEEDS_REVIEW, 20, 0) }
    }

    @Test
    fun `내부 원장이 연결된 건은 재조회 대상으로 분류한다`() {
        givenList(attempt(1L, 4242L))

        val item = useCase.list(0, 20).content.single()

        assertEquals("APPROVAL_MISMATCH", item.cause)
        assertEquals("ongo-4242", item.externalPaymentId)
        assertEquals(PaymentStatus.PENDING.name, item.paymentStatus)
        assertEquals(19_900, item.paymentAmount)
        assertEquals("KRW", item.paymentCurrency)
        assertEquals(SubscriptionStatus.ACTIVE.name, item.subscriptionStatus)
    }

    /** 내부 원장이 없으면 코드가 확정할 수 없다. 운영자가 재조회를 누르기 전에 알아야 한다. */
    @Test
    fun `내부 원장이 없는 건은 수기 대사 대상으로 분류한다`() {
        givenList(attempt(2L, null))

        val item = useCase.list(0, 20).content.single()

        assertEquals("LEGACY_NO_INTERNAL_LEDGER", item.cause)
        assertNull(item.externalPaymentId)
        assertNull(item.paymentStatus)
        assertTrue(item.reason.contains("수기"))
    }

    /** 페이지 크기를 그대로 믿으면 한 번에 전 건을 끌어와 운영 DB를 흔들 수 있다. */
    @Test
    fun `페이지 파라미터는 안전한 범위로 자른다`() {
        givenList()

        useCase.list(page = -3, size = 10_000)

        verify { attemptRepository.findByOutcome(any(), 100, 0) }
    }

    /* ---- 노출 금지 ---- */

    /**
     * 필드 이름으로 고정한다. 나중에 누가 "운영자가 보면 편하니까" 로 결제 수단이나 이메일을
     * 더하면 여기서 먼저 깨진다.
     */
    @Test
    fun `응답 DTO 에 결제 수단이나 개인정보 필드를 두지 않는다`() {
        val forbidden = listOf("billingkey", "email", "card", "secret", "token", "password", "apikey")

        val fields = AdminRenewalReviewItem::class.memberProperties.map { it.name.lowercase() }

        forbidden.forEach { banned ->
            assertFalse(
                fields.any { it.contains(banned) },
                "관리자 응답에 금지된 필드가 있다: $banned (fields=$fields)",
            )
        }
    }

    @Test
    fun `응답 값에 빌링키가 섞이지 않는다`() {
        givenList(attempt(1L, 4242L))

        val serialized = useCase.list(0, 20).content.single().toString()

        assertFalse(serialized.contains("enc-billing-key-should-never-leave"), serialized)
    }

    /* ---- 재조회 ---- */

    @Test
    fun `재조회 결과를 그대로 전달한다`() {
        every { renewalService.recheckReview(77L, any()) } returns RenewalReviewRecheck(
            RenewalReviewDecision.RESOLVED,
            "PG 재조회 결과로 확정했습니다.",
            SubscriptionRenewalOutcome.CHARGED,
        )

        val result = useCase.recheck(77L)

        assertEquals("RESOLVED", result.decision)
        assertEquals("CHARGED", result.outcome)
        assertTrue(result.changed)
    }

    /**
     * PG 장애 원문을 응답에 실으면 내부 구조가 드러나고, 운영자는 "실패" 를 "미결제" 로
     * 오해할 수 있다. 상태가 그대로라는 사실만 안전한 문장으로 알린다.
     */
    @Test
    fun `조회 실패는 안전한 문구로 바꾸고 상태가 그대로임을 알린다`() {
        every { renewalService.recheckReview(77L, any()) } throws
            IllegalStateException("PortOne 500 at https://api.portone.io/payments/ongo-4242")

        val result = useCase.recheck(77L)

        assertEquals("LOOKUP_FAILED", result.decision)
        assertFalse(result.changed)
        assertNull(result.outcome)
        assertFalse(result.reason.contains("portone.io"), result.reason)
        assertFalse(result.reason.contains("500"), result.reason)
    }
}
