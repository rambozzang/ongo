package com.ongo.application.credit

import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.CreditTransactionType
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.CreditNotFoundException
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.AiPurchasedCredit
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.event.CreditDeductedEvent
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class CreditServiceTest {

    @MockK
    private lateinit var creditRepository: CreditRepository

    @MockK(relaxed = true)
    private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMockKs
    private lateinit var creditService: CreditService

    private val userId = 1L
    private val now = LocalDateTime.now()
    private val resetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1)

    private fun createCredit(
        id: Long = 1L,
        balance: Int = 100,
        freeMonthly: Int = 30,
        freeRemaining: Int = 20,
    ) = AiCredit(
        id = id,
        userId = userId,
        balance = balance,
        freeMonthly = freeMonthly,
        freeRemaining = freeRemaining,
        freeResetDate = resetDate,
    )

    // ──────────────────────────────────────────────
    // 1. revokeCredits 정상 차감
    // ──────────────────────────────────────────────
    private fun purchased(remaining: Int, id: Long = 1L) = AiPurchasedCredit(
        id = id,
        userId = userId,
        packageName = "STARTER",
        totalCredits = remaining,
        remaining = remaining,
        price = 4_900,
        purchasedAt = now,
        expiresAt = now.plusDays(30),
    )

    @Test
    fun `revokeCredits should revoke from purchased credits and leave free credits untouched`() {
        val credit = createCredit(balance = 100, freeRemaining = 20)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns listOf(purchased(100))
        every { creditRepository.updatePurchasedCredit(any()) } answers { firstArg() }
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.revokeCredits(userId, 30, "ADMIN_REVOKE")

        verify {
            creditRepository.updatePurchasedCredit(match<AiPurchasedCredit> { it.remaining == 70 })
        }
        verify {
            creditRepository.update(match<AiCredit> {
                it.balance == 70 && it.freeRemaining == 20
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.REVOKE &&
                    it.amount == -30 &&
                    it.balanceAfter == 70 &&
                    it.feature == "ADMIN_REVOKE"
            })
        }
    }

    // ──────────────────────────────────────────────
    // 2. 구매 잔여분보다 많이 회수 요청해도 무료 크레딧은 건드리지 않는다
    // ──────────────────────────────────────────────
    @Test
    fun `revokeCredits should cap at purchased remaining and never touch free credits`() {
        val credit = createCredit(balance = 10, freeRemaining = 5)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns listOf(purchased(10))
        every { creditRepository.updatePurchasedCredit(any()) } answers { firstArg() }
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.revokeCredits(userId, 50, "ADMIN_REVOKE")

        // 있는 만큼(10)만 회수되고, 무료 크레딧 5 는 그대로 남아야 한다.
        verify {
            creditRepository.update(match<AiCredit> {
                it.balance == 0 && it.freeRemaining == 5
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.balanceAfter == 0 && it.amount == -10
            })
        }
    }

    // ──────────────────────────────────────────────
    // applyPlanEntitlement — 플랜 전환 시 무료 크레딧 권한
    // ──────────────────────────────────────────────

    /**
     * 결제·체험 직후 크레딧이 FREE 기준(30)에 머물면 쇼츠 실행 한 번(37)도 못 돌린다.
     * 구독만 바뀌고 쓸 수 있는 것은 그대로인 상태가 이 API 가 없앤 것이다.
     */
    @Test
    fun `유료 플랜 부여는 freeMonthly 와 freeRemaining 을 플랜 값으로 설정한다`() {
        // 이번 달 이미 12 를 쓴 FREE 사용자
        val credit = createCredit(balance = 18, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.STARTER, "TRIAL_START")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeMonthly == PlanType.STARTER.freeCredits &&
                    it.freeRemaining == PlanType.STARTER.freeCredits &&
                    it.balance == PlanType.STARTER.freeCredits
            })
        }
        // 100 이면 파이프라인 한 번(37)을 완주할 수 있다.
        kotlin.test.assertTrue(PlanType.STARTER.freeCredits >= 37)
    }

    @Test
    fun `유료 플랜 부여는 구매 크레딧을 보존하고 balance 에 합산한다`() {
        val credit = createCredit(balance = 518, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns
            listOf(purchased(remaining = 500))
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.STARTER, "SUBSCRIPTION_PAID")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 100 && it.balance == 600
            })
        }
        // 구매분은 플랜과 무관한 별도 원장이라 건드리지 않는다.
        verify(exactly = 0) { creditRepository.updatePurchasedCredit(any()) }
    }

    /* 하향에서 올려주면 유료 권한이 무료 사용자에게 새어나간다. */
    @Test
    fun `FREE 하향은 freeRemaining 을 기존 값과 min 으로 내린다`() {
        val credit = createCredit(balance = 95, freeMonthly = 100, freeRemaining = 95)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.FREE, "TRIAL_EXPIRED")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeMonthly == PlanType.FREE.freeCredits &&
                    it.freeRemaining == PlanType.FREE.freeCredits &&
                    it.balance == PlanType.FREE.freeCredits
            })
        }
    }

    @Test
    fun `FREE 하향은 이미 적게 남은 잔여를 올려주지 않는다`() {
        val credit = createCredit(balance = 5, freeMonthly = 100, freeRemaining = 5)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.FREE, "PAST_DUE")

        verify { creditRepository.update(match<AiCredit> { it.freeRemaining == 5 && it.balance == 5 }) }
    }

    /* freeResetDate 가 바뀌면 주기를 당기려고 플랜을 오가는 경로가 생긴다. */
    @Test
    fun `플랜 전환은 freeResetDate 를 바꾸지 않는다`() {
        val credit = createCredit(balance = 18, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.PRO, "SUBSCRIPTION_PAID")

        verify { creditRepository.update(match<AiCredit> { it.freeResetDate == resetDate }) }
    }

    /**
     * 새 enum 값은 마이그레이션과 함께 넣지 않으면 삽입 시점에 깨진다.
     * REVOKE 가 정확히 그렇게 누락돼 있었으므로 여기서는 기존 값만 쓴다.
     */
    @Test
    fun `전환 감사는 기존 FREE_RESET 타입과 사유 태그로 남는다`() {
        val credit = createCredit(balance = 18, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.STARTER, "TRIAL_START")

        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.FREE_RESET &&
                    it.feature == "PLAN_ENTITLEMENT:TRIAL_START:STARTER" &&
                    // 증감분이다. 전체값을 넣으면 합계가 부풀어 원장을 못 쓴다.
                    it.amount == 82 &&
                    it.balanceAfter == 100
            })
        }
    }

    /* 전환이 일어났다는 사실 자체가 감사 대상이다. */
    @Test
    fun `증감이 0 이어도 전환 감사를 남긴다`() {
        val credit = createCredit(balance = 100, freeMonthly = 100, freeRemaining = 100)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.STARTER, "SUBSCRIPTION_PAID")

        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> { it.amount == 0 })
        }
    }

    /* 두 경로가 반대 순서로 잠그면 교착이 생긴다. revokeCredits 와 같은 순서를 쓴다. */
    @Test
    fun `ai_credits 를 먼저 잠그고 구매분을 잠근다`() {
        val credit = createCredit(balance = 18, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.applyPlanEntitlement(userId, PlanType.STARTER, "TRIAL_START")

        io.mockk.verifyOrder {
            creditRepository.findByUserIdForUpdate(userId)
            creditRepository.findActivePurchasedCreditsForUpdate(userId)
            creditRepository.update(any())
        }
    }

    // ──────────────────────────────────────────────
    // 3. refundCredit 정상 복구
    // ──────────────────────────────────────────────
    @Test
    fun `refundCredit should increase balance and freeRemaining capped at freeMonthly`() {
        // balance 는 저장값을 더하는 대신 freeRemaining + 활성 구매분으로 다시 센다.
        val credit = createCredit(balance = 20, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.balance == 23 && it.freeRemaining == 23
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.REFUND &&
                    it.amount == 5 &&
                    it.balanceAfter == 23
            })
        }
    }

    // ──────────────────────────────────────────────
    // 4. refundCredit freeMonthly 초과 방지
    // ──────────────────────────────────────────────
    @Test
    fun `refundCredit should not exceed freeMonthly for freeRemaining`() {
        val credit = createCredit(balance = 25, freeMonthly = 30, freeRemaining = 28)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 30 && it.balance == 30
            })
        }
    }

    /**
     * clamp 로 잘린 만큼이 balance 에 남는 phantom 잔액.
     *
     * 예전에는 `balance + amount` 였다. freeRemaining 은 freeMonthly 로 잘려 2 만 늘어나는데
     * balance 에는 10 을 다 더해서, 어디에도 없는 8 이 잔액으로 남았다. 그 잔액으로는
     * 차감이 되지만 실제 무료·구매분 어느 쪽에서도 뺄 것이 없다.
     */
    @Test
    fun `refundCredit 은 clamp 후에도 balance 가 freeRemaining 과 구매분 합과 같다`() {
        val credit = createCredit(balance = 28, freeMonthly = 30, freeRemaining = 28)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 10, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                // 28+10=38 이 아니라 clamp 된 30. 구매분이 없으므로 balance 도 30.
                it.freeRemaining == 30 && it.balance == 30
            })
        }
    }

    /** 구매 크레딧이 있으면 balance 는 무료 잔여와 구매 잔여의 합이다. */
    @Test
    fun `refundCredit 은 구매 크레딧을 balance 합계에 포함한다`() {
        val credit = createCredit(balance = 118, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCredits(userId) } returns
            listOf(purchased(remaining = 100))
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 23 && it.balance == 123
            })
        }
    }

    // ──────────────────────────────────────────────
    // 5. validateAndDeduct 잔액 부족
    // ──────────────────────────────────────────────
    @Test
    fun `validateAndDeduct should throw InsufficientCreditException when balance is insufficient`() {
        val credit = createCredit(balance = 2, freeRemaining = 2)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()

        val exception = assertFailsWith<InsufficientCreditException> {
            creditService.validateAndDeduct(userId, AiFeature.META_GENERATION) // cost=5
        }

        assertEquals(5, exception.required)
        assertEquals(2, exception.available)

        verify(exactly = 0) { creditRepository.update(any()) }
        verify(exactly = 0) { creditRepository.saveTransaction(any()) }
    }
}
