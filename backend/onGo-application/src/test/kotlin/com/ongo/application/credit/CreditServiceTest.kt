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
import com.ongo.application.subscription.DummyTransactionManagerForTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.jvm.kotlinFunction
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class CreditServiceTest {

    @MockK
    private lateinit var creditRepository: CreditRepository

    @MockK(relaxed = true)
    private lateinit var eventPublisher: ApplicationEventPublisher

    /**
     * `withCredits` 의 커밋 경계용. 콜백을 그대로 실행하므로 이 테스트가 보는 것은
     * **어떤 순서로 무엇이 호출되는가**이며, 실제 커밋·롤백은 통합 테스트의 몫이다.
     */
    private val transactionManager: PlatformTransactionManager = DummyTransactionManagerForTest()

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
    // 3~4. 출처 불명 환불(refundCredit)은 비활성이다
    // ──────────────────────────────────────────────
    //
    // 예전에는 이 메서드가 실제로 동작했고, 아래 네 개의 테스트가 그 동작을 고정했다 —
    // "무료분을 freeMonthly 로 clamp 해서 늘린다", "balance 를 다시 센다" 같은 것들이다.
    // 그 계산 자체는 맞았지만 **전제가 틀렸다.** 차감이 구매 패키지에서 나갔어도 언제나
    // 무료분에 얹었기 때문에, 만료 없는 구매 크레딧이 월말에 사라지는 무료 크레딧으로
    // 바뀌고 clamp 에 걸린 몫은 그대로 증발했다.
    //
    // 그래서 계산을 검증하는 대신 **그 경로가 아예 실행되지 않는다**를 검증한다.
    // 운영 호출자는 전부 refundAllocation 으로 옮겼고, 이 심볼은 fail-closed 다.

    /**
     * **부르면 던진다.** 조용히 잘못 환불하는 것보다 시끄럽게 실패하는 편이 낫다.
     *
     * `@Deprecated(level = ERROR)` 가 Kotlin 호출부를 컴파일 단계에서 막지만, Java 나
     * 리플렉션 경로는 그것을 우회한다. 런타임 방어가 함께 필요한 이유다.
     */
    @Test
    fun `출처 불명 환불은 호출 즉시 실패한다`() {
        assertFailsWith<UnsupportedOperationException> { invokeLegacyRefund(userId, 5, "META_GENERATION") }
    }

    /**
     * **아무것도 쓰지 않아야 한다.** 던지기 전에 행을 건드리면 잔액만 어긋난 채 실패한다.
     */
    @Test
    fun `출처 불명 환불은 크레딧 행도 원장도 건드리지 않는다`() {
        assertFailsWith<UnsupportedOperationException> { invokeLegacyRefund(userId, 5, "META_GENERATION") }

        verify(exactly = 0) { creditRepository.findByUserIdForUpdate(any()) }
        verify(exactly = 0) { creditRepository.update(any()) }
        verify(exactly = 0) { creditRepository.updatePurchasedCredit(any()) }
        verify(exactly = 0) { creditRepository.saveTransaction(any()) }
    }

    /** 예외 메시지가 대체 수단을 알려주지 않으면 다음 사람이 또 우회로를 찾는다. */
    @Test
    fun `출처 불명 환불 예외는 대체 API 를 안내한다`() {
        val error = assertFailsWith<UnsupportedOperationException> {
            invokeLegacyRefund(userId, 5, "META_GENERATION")
        }

        assertTrue(
            error.message!!.contains("refundAllocation"),
            "대체 수단 안내가 없다: ${error.message}",
        )
    }

    /**
     * Kotlin 호출부는 컴파일되지 않아야 한다. 그것이 가장 강한 방어다 —
     * 런타임 예외는 배포 후에야 드러나지만 컴파일 오류는 그 전에 막는다.
     */
    @Test
    fun `출처 불명 환불은 DeprecationLevel ERROR 로 막혀 있다`() {
        val method = CreditService::class.java.methods.single { it.name == "refundCredit" }
        val deprecated = method.kotlinFunction?.annotations?.filterIsInstance<Deprecated>()?.singleOrNull()

        assertNotNull(deprecated, "@Deprecated 가 없다 — 호출부가 경고 없이 컴파일된다")
        assertEquals(
            DeprecationLevel.ERROR,
            deprecated.level,
            "WARNING 은 무시하기 쉽다. 호출 자체를 컴파일 오류로 막아야 한다",
        )
    }

    /**
     * `@Deprecated(level = ERROR)` 때문에 Kotlin 에서 직접 부를 수 없다. 런타임 동작을
     * 검증하려면 리플렉션으로 우회해야 한다 — Java·리플렉션 호출자가 실제로 겪는 경로다.
     */
    private fun invokeLegacyRefund(userId: Long, amount: Int, featureName: String) {
        val method = CreditService::class.java.methods.single { it.name == "refundCredit" }
        try {
            method.invoke(creditService, userId, amount, featureName)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
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

    // ──────────────────────────────────────────────
    // withCredits — 차감 확정 후 외부 호출, 실패 시 환불
    // ──────────────────────────────────────────────

    /**
     * 유료 AI 유스케이스가 통째로 `@Transactional` 이던 시절에는 예외가 나면 바깥 롤백이
     * 차감을 지웠고, catch 안의 `refundCredit` 은 함께 롤백되는 **죽은 코드**였다.
     * 차감을 먼저 커밋하는 지금은 환불이 실제로 일어나야 한다 — 그러지 않으면 고객이
     * 받지 못한 결과에 크레딧을 낸다.
     */
    private fun stubCreditRow(freeRemaining: Int = 20, purchasedRemaining: Int = 100) {
        val credit = createCredit(freeRemaining = freeRemaining)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns
            listOf(purchased(purchasedRemaining))
        every { creditRepository.findActivePurchasedCredits(userId) } returns
            listOf(purchased(purchasedRemaining))
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.updatePurchasedCredit(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }
        // 환불은 차감 영수증에 적힌 패키지 id 로 행을 잠근다. 소진된 패키지도 되돌려야
        // 하므로 status 로 거르는 findActive* 로는 찾을 수 없다.
        every { creditRepository.findPurchasedCreditsByIdsForUpdate(userId, any()) } answers {
            val ids = secondArg<Collection<Long>>()
            listOf(purchased(purchasedRemaining)).filter { it.id in ids }
        }
    }

    @Test
    fun `withCredits 는 블록 실행 전에 차감한다`() {
        stubCreditRow()
        val order = mutableListOf<String>()
        every { creditRepository.saveTransaction(any()) } answers { order += "credit"; firstArg() }

        val result = creditService.withCredits(userId, 5, "TEST_FEATURE") {
            order += "call"
            "ok"
        }

        assertEquals("ok", result)
        // 차감이 외부 호출보다 먼저다. 순서가 뒤집히면 잔액 없는 사용자도 모델을 부른다.
        assertEquals(listOf("credit", "call"), order)
    }

    @Test
    fun `withCredits 는 블록이 실패하면 환불하고 원래 예외를 그대로 올린다`() {
        stubCreditRow()
        val transactions = mutableListOf<CreditTransactionType>()
        every { creditRepository.saveTransaction(any()) } answers {
            transactions += firstArg<AiCreditTransaction>().type
            firstArg()
        }

        val error = assertFailsWith<IllegalStateException> {
            creditService.withCredits(userId, 5, "TEST_FEATURE") {
                throw IllegalStateException("모델 호출 실패")
            }
        }

        assertEquals("모델 호출 실패", error.message)
        // 차감과 환불이 각각 원장에 남아야 대사가 가능하다.
        assertEquals(listOf(CreditTransactionType.DEDUCT, CreditTransactionType.REFUND), transactions)
    }

    /** 잔액이 없으면 모델을 부르지 않는다. 부르면 돈은 나가고 크레딧은 못 받는다. */
    @Test
    fun `withCredits 는 잔액이 부족하면 블록을 실행하지 않는다`() {
        val credit = createCredit(freeRemaining = 1)
        every { creditRepository.findByUserIdForUpdate(userId) } returns credit
        every { creditRepository.findActivePurchasedCreditsForUpdate(userId) } returns emptyList()
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        var called = false

        assertFailsWith<InsufficientCreditException> {
            creditService.withCredits(userId, 5, "TEST_FEATURE") { called = true }
        }

        assertEquals(false, called)
        verify(exactly = 0) { creditRepository.update(any()) }
    }

    /** 환불 자체가 실패해도 사용자가 보는 것은 원래 오류여야 한다. */
    @Test
    fun `withCredits 는 환불 실패가 원래 오류를 가리지 않는다`() {
        stubCreditRow()
        every { creditRepository.update(any()) } answers { firstArg() } andThenThrows
            IllegalStateException("환불 저장 실패")

        val error = assertFailsWith<IllegalStateException> {
            creditService.withCredits(userId, 5, "TEST_FEATURE") {
                throw IllegalStateException("모델 호출 실패")
            }
        }

        assertEquals("모델 호출 실패", error.message)
    }

    // ──────────────────────────────────────────────
    // withCredits — 트랜잭션 경계 자체
    // ──────────────────────────────────────────────

    /**
     * 요청한 전파 방식과 커밋 시점을 기록하는 매니저.
     *
     * `DummyTransactionManagerForTest` 는 commit/rollback 을 무시하므로 "경계를 요청했는가"
     * 를 증명하지 못한다. 여기서는 그 요청 자체를 관찰한다 — 실제 DB 가 정말 새 트랜잭션을
     * 열었는지는 Testcontainers 통합 테스트의 몫이고, 이 테스트가 그것을 대체한다고
     * 주장하지 않는다.
     */
    private class RecordingTransactionManager : PlatformTransactionManager {
        val events = mutableListOf<String>()
        val propagations = mutableListOf<Int>()

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            propagations += definition?.propagationBehavior ?: -1
            events += "begin"
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) {
            events += "commit"
        }

        override fun rollback(status: TransactionStatus) {
            events += "rollback"
        }
    }

    private fun serviceWith(manager: PlatformTransactionManager) =
        CreditService(creditRepository, eventPublisher, manager)

    /**
     * **차감은 호출자의 트랜잭션에 참여하면 안 된다.**
     *
     * 참여하면 그 트랜잭션이 끝날 때까지 커밋되지 않아, "차감을 먼저 확정한다" 는 전제가
     * 무너지고 `ai_credits` 잠금이 LLM 응답 시간만큼 유지된다.
     */
    @Test
    fun `withCredits 는 차감과 환불 모두 REQUIRES_NEW 경계를 요청한다`() {
        stubCreditRow()
        val manager = RecordingTransactionManager()

        assertFailsWith<IllegalStateException> {
            serviceWith(manager).withCredits(userId, 5, "TEST_FEATURE") {
                throw IllegalStateException("모델 호출 실패")
            }
        }

        // 차감 1회 + 환불 1회
        assertEquals(2, manager.propagations.size, "경계가 두 번 열려야 한다: ${manager.propagations}")
        manager.propagations.forEach {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, it)
        }
    }

    /** 차감 트랜잭션은 블록 실행 **전에** 닫혀야 한다. */
    @Test
    fun `withCredits 는 블록 실행 전에 차감 트랜잭션을 커밋한다`() {
        stubCreditRow()
        val manager = RecordingTransactionManager()

        serviceWith(manager).withCredits(userId, 5, "TEST_FEATURE") {
            manager.events += "call"
        }

        assertEquals(listOf("begin", "commit", "call"), manager.events)
    }

    /** 실패 경로에서는 차감 커밋 뒤에 환불 경계가 따로 열린다. */
    @Test
    fun `withCredits 실패 시 차감 커밋 뒤에 환불 경계를 연다`() {
        stubCreditRow()
        val manager = RecordingTransactionManager()

        assertFailsWith<IllegalStateException> {
            serviceWith(manager).withCredits(userId, 5, "TEST_FEATURE") {
                manager.events += "call"
                throw IllegalStateException("모델 호출 실패")
            }
        }

        assertEquals(listOf("begin", "commit", "call", "begin", "commit"), manager.events)
    }
}
