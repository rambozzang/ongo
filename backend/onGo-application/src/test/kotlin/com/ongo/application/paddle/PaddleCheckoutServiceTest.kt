package com.ongo.application.paddle

import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.exception.CreditNotFoundException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Paddle 체크아웃이 **결제창을 열기 전에** 크레딧 원장을 확인하는지 고정한다.
 *
 * ## 무엇이 문제였나
 *
 * Paddle 결제 완료(`PaddleWebhookService.handleSubscriptionCreated`)는 마지막에
 * `creditService.applyPlanEntitlement` 를 부른다. 원장이 없으면 거기서 예외가 나는데,
 * **그 시점에는 Paddle 청구가 이미 끝나 있다.** 웹훅 처리는 실패로 남아 재시도되지만 매번
 * 같은 지점에서 터지고, 원장을 만들어 주는 코드 경로는 어디에도 없다
 * (`CreditService.initializeCredits` 는 호출처가 0건이다).
 *
 * 그래서 돈이 움직이기 전에 판정한다. 여기서 막으면 가격 ID 조회도, 프론트에 넘길
 * 체크아웃 데이터 생성도 일어나지 않아 결제창 자체가 열리지 않는다.
 *
 * ## 왜 원장을 만들어 주지 않는가
 *
 * 없는 원장을 자동 생성하면 그 계정의 과거 상태를 추측하게 된다 — 이번 달 무료분을 이미
 * 썼는지 알 수 없어, 후하게 잡으면 없던 크레딧이 생기고 박하게 잡으면 쓴 적 없는 몫을
 * 빼앗는다. 원장 부재는 데이터 사고이지 초기화가 필요한 정상 상태가 아니다.
 */
class PaddleCheckoutServiceTest {

    private val paddleGateway = mockk<PaddleGateway>()
    private val userRepository = mockk<UserRepository>()
    private val creditService = mockk<CreditService>()

    private val service = PaddleCheckoutService(
        paddleGateway = paddleGateway,
        userRepository = userRepository,
        creditService = creditService,
    )

    private val userId = 7L

    @BeforeEach
    fun setUp() {
        every { userRepository.findById(userId) } returns User(
            id = userId,
            email = "creator@example.com",
            name = "크리에이터",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )
        // 정상 사용자는 가입 시 원장이 만들어져 있다. 부재는 각 테스트가 따로 만든다.
        every { creditService.ensureAccountPresence(userId) } returns Unit
        every { paddleGateway.getPriceIdForPlan(any(), any()) } returns "pri_starter"
        every { paddleGateway.getPriceIdForCreditPackage(any()) } returns "pri_credit_basic"
    }

    private fun givenLedgerMissing() {
        every { creditService.ensureAccountPresence(userId) } throws CreditNotFoundException(userId)
    }

    // ── 원장 부재 → 차단 ─────────────────────────────────────────────────────

    /** 가격 조회도 체크아웃 데이터 생성도 일어나면 안 된다. */
    @Test
    @DisplayName("원장이 없으면 구독 체크아웃이 가격 조회 전에 거절한다")
    fun subscriptionCheckoutBlockedWhenLedgerMissing() {
        givenLedgerMissing()

        val error = assertThrows(CreditNotFoundException::class.java) {
            service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")
        }

        assertEquals("CREDIT_NOT_FOUND", error.code)
        verify(exactly = 0) { paddleGateway.getPriceIdForPlan(any(), any()) }
    }

    @Test
    @DisplayName("원장이 없으면 크레딧 체크아웃이 가격 조회 전에 거절한다")
    fun creditCheckoutBlockedWhenLedgerMissing() {
        givenLedgerMissing()

        val error = assertThrows(CreditNotFoundException::class.java) {
            service.createCreditCheckout(userId, "BASIC")
        }

        assertEquals("CREDIT_NOT_FOUND", error.code)
        verify(exactly = 0) { paddleGateway.getPriceIdForCreditPackage(any()) }
    }

    // ── 정상 통과 ────────────────────────────────────────────────────────────

    /** 원장이 있으면 종전대로 체크아웃 데이터를 만든다 — 가드가 과하지 않은지 본다. */
    @Test
    @DisplayName("원장이 있으면 구독 체크아웃이 종전대로 데이터를 만든다")
    fun subscriptionCheckoutSucceedsWhenLedgerPresent() {
        val data = service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")

        assertEquals("pri_starter", data.priceId)
        assertEquals("creator@example.com", data.customerEmail)
        assertEquals(mapOf("user_id" to userId), data.customData)
        verify(exactly = 1) { creditService.ensureAccountPresence(userId) }
        verify(exactly = 1) { paddleGateway.getPriceIdForPlan("STARTER", "MONTHLY") }
    }

    @Test
    @DisplayName("원장이 있으면 크레딧 체크아웃이 종전대로 데이터를 만든다")
    fun creditCheckoutSucceedsWhenLedgerPresent() {
        val data = service.createCreditCheckout(userId, "BASIC")

        assertEquals("pri_credit_basic", data.priceId)
        assertEquals("creator@example.com", data.customerEmail)
        verify(exactly = 1) { creditService.ensureAccountPresence(userId) }
        verify(exactly = 1) { paddleGateway.getPriceIdForCreditPackage("BASIC") }
    }

    // ── 기존 동작 보존 ───────────────────────────────────────────────────────

    /**
     * 없는 사용자는 **종전대로** 사용자 없음으로 끝나야 한다.
     *
     * 원장 가드를 사용자 조회보다 앞에 두면 존재하지 않는 계정이 CREDIT_NOT_FOUND 로
     * 보고돼 원인을 오해하게 된다.
     */
    @Test
    @DisplayName("없는 사용자는 종전대로 사용자 없음으로 거절한다")
    fun unknownUserStillReportedAsUserNotFound() {
        every { userRepository.findById(999L) } returns null

        assertThrows(NotFoundException::class.java) {
            service.createSubscriptionCheckout(999L, "STARTER", "MONTHLY")
        }

        verify(exactly = 0) { creditService.ensureAccountPresence(999L) }
        verify(exactly = 0) { paddleGateway.getPriceIdForPlan(any(), any()) }
    }

    /** 가격 ID 를 찾지 못하는 기존 실패 경로는 그대로다. */
    @Test
    @DisplayName("가격 ID 가 없으면 종전대로 거절한다")
    fun missingPriceIdStillRejected() {
        every { paddleGateway.getPriceIdForPlan(any(), any()) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")
        }
    }
}
