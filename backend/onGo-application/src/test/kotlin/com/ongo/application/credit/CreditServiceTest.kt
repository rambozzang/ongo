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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreditServiceTest {

    private val creditRepository = mockk<CreditRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private lateinit var creditService: CreditService

    private val userId = 1L
    private val now = LocalDateTime.now()
    private val resetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        creditService = CreditService(creditRepository, eventPublisher)
    }

    @Test
    fun `validateAndDeduct should deduct from free credits only when sufficient`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 30, freeMonthly = 30,
            freeRemaining = 30, freeResetDate = resetDate,
        )
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.validateAndDeduct(userId, AiFeature.META_GENERATION)

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 25 && it.balance == 25
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.DEDUCT && it.amount == -5 && it.balanceAfter == 25
            })
        }
        verify {
            eventPublisher.publishEvent(match<CreditDeductedEvent> {
                it.userId == userId && it.amount == 5 && it.feature == AiFeature.META_GENERATION && it.remainingBalance == 25
            })
        }
        verify(exactly = 0) { creditRepository.updatePurchasedCredit(any()) }
    }

    @Test
    fun `validateAndDeduct should deduct from free then purchased credits`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 13, freeMonthly = 30,
            freeRemaining = 3, freeResetDate = resetDate,
        )
        val purchasedCredit = AiPurchasedCredit(
            id = 10L, userId = userId, packageName = "BASIC",
            totalCredits = 100, remaining = 10, price = 5000,
            expiresAt = now.plusDays(30), status = "ACTIVE",
        )
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns listOf(purchasedCredit)
        every { creditRepository.updatePurchasedCredit(any()) } answers { firstArg() }
        // After deduction: free 3→0 (deduct 3), purchased 10→3 (deduct 7). findActivePurchasedCredits returns updated remaining.
        every { creditRepository.findActivePurchasedCredits(userId) } returns listOf(purchasedCredit.copy(remaining = 3))
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.validateAndDeduct(userId, AiFeature.STT) // cost=10

        verify {
            creditRepository.updatePurchasedCredit(match<AiPurchasedCredit> {
                it.id == 10L && it.remaining == 3 && it.status == "ACTIVE"
            })
        }
        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 0 && it.balance == 3
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.amount == -10 && it.balanceAfter == 3
            })
        }
    }

    @Test
    fun `validateAndDeduct should throw InsufficientCreditException when not enough credits`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 2, freeMonthly = 30,
            freeRemaining = 2, freeResetDate = resetDate,
        )
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

    @Test
    fun `validateAndDeduct should throw CreditNotFoundException when no credit record`() {
        every { creditRepository.findByUserIdForUpdate(userId) } returns null

        assertFailsWith<CreditNotFoundException> {
            creditService.validateAndDeduct(userId, AiFeature.META_GENERATION)
        }

        verify(exactly = 0) { creditRepository.findActivePurchasedCreditsForUpdate(any()) }
    }

    @Test
    fun `validateAndDeduct should exhaust purchased credit and mark as EXHAUSTED`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 5, freeMonthly = 30,
            freeRemaining = 0, freeResetDate = resetDate,
        )
        val purchasedCredit = AiPurchasedCredit(
            id = 10L, userId = userId, packageName = "BASIC",
            totalCredits = 100, remaining = 5, price = 5000,
            expiresAt = now.plusDays(30), status = "ACTIVE",
        )
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns listOf(purchasedCredit)
        every { creditRepository.updatePurchasedCredit(any()) } answers { firstArg() }
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.validateAndDeduct(userId, AiFeature.META_GENERATION) // cost=5

        verify {
            creditRepository.updatePurchasedCredit(match<AiPurchasedCredit> {
                it.id == 10L && it.remaining == 0 && it.status == "EXHAUSTED"
            })
        }
        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 0 && it.balance == 0
            })
        }
    }

    @Test
    fun `getBalance should return correct balance info`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 50, freeMonthly = 30,
            freeRemaining = 20, freeResetDate = resetDate,
        )
        val purchasedCredits = listOf(
            AiPurchasedCredit(
                id = 10L, userId = userId, packageName = "BASIC",
                totalCredits = 100, remaining = 30, price = 5000,
                expiresAt = now.plusDays(30), status = "ACTIVE",
            ),
        )
        every { creditRepository.findByUserId(userId) } returns credit
        every { creditRepository.findActivePurchasedCredits(userId) } returns purchasedCredits

        val result = creditService.getBalance(userId)

        assertEquals(50, result.totalBalance)
        assertEquals(20, result.freeRemaining)
        assertEquals(30, result.freeMonthly)
        assertEquals(30, result.purchasedBalance)
        assertEquals(resetDate, result.freeResetDate)
    }

    @Test
    fun `getBalance should return defaults when no credit record`() {
        every { creditRepository.findByUserId(userId) } returns null

        val result = creditService.getBalance(userId)

        assertEquals(0, result.totalBalance)
        assertEquals(0, result.freeRemaining)
        assertEquals(0, result.freeMonthly)
        assertEquals(0, result.purchasedBalance)
    }

    @Test
    fun `refundCredit should cap freeRemaining at freeMonthly`() {
        val credit = AiCredit(
            id = 1L, userId = userId, balance = 25, freeMonthly = 30,
            freeRemaining = 28, freeResetDate = resetDate,
        )
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        creditService.refundCredit(userId, 5, "META_GENERATION")

        verify {
            creditRepository.update(match<AiCredit> {
                it.freeRemaining == 30 && it.balance == 30
            })
        }
        verify {
            creditRepository.saveTransaction(match<AiCreditTransaction> {
                it.type == CreditTransactionType.REFUND && it.amount == 5 && it.balanceAfter == 30
            })
        }
    }
}
