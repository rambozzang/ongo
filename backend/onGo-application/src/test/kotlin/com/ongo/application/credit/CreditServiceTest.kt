package com.ongo.application.credit

import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.CreditTransactionType
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
    // 3. refundCredit 정상 복구
    // ──────────────────────────────────────────────
    @Test
    fun `refundCredit should increase balance and freeRemaining capped at freeMonthly`() {
        val credit = createCredit(balance = 20, freeMonthly = 30, freeRemaining = 18)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.balance == 25 && it.freeRemaining == 23
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.REFUND &&
                    it.amount == 5 &&
                    it.balanceAfter == 25
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
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 30 && it.balance == 30
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
