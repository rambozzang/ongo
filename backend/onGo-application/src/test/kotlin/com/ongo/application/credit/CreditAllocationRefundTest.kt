package com.ongo.application.credit

import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.AiPurchasedCredit
import com.ongo.domain.credit.CreditRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 크레딧 **출처별 정확한 환불**을 고정한다.
 *
 * ## 무엇이 고객 자산을 깎아먹었나
 *
 * [CreditService.validateAndDeduct] 는 무료 → 구매(만료 임박순) 순으로 차감한다. 그런데
 * 예전 `refundCredit` 은 출처를 모른 채 언제나 `freeRemaining += amount` 만 했다.
 *
 * - `free=0, purchased=100` 에서 5 를 쓰고 실패 → 만료 없는 **구매분 5 가 월말에 사라지는
 *   무료분 5 로 바뀐다.** 돈 주고 산 자산이 유통기한 있는 것이 된다.
 * - `free=2, purchased=100` 에서 5 를 쓰고 실패 → `freeMonthly` clamp 로 2 만 돌아오고
 *   **구매분 3 은 어디에도 복구되지 않는다.** 그냥 없어진다.
 *
 * 잔액 총합만 맞추는 방식으로는 둘 다 막을 수 없다. 차감이 영수증
 * ([CreditAllocation])을 돌려주고 환불이 그것을 되짚는다.
 *
 * 여기서는 인메모리 저장소로 **실제 행 상태**(무료 잔여·패키지별 remaining·status)를
 * 확인한다. mock 의 호출 인자만 보면 "복구했다고 주장했다"까지밖에 모른다.
 */
class CreditAllocationRefundTest {

    private val userId = 7L

    /** 트랜잭션 경계는 이 테스트의 관심사가 아니다. 콜백을 그대로 실행한다. */
    private val transactionManager = mockk<PlatformTransactionManager>().also {
        every { it.getTransaction(any<TransactionDefinition>()) } returns SimpleTransactionStatus()
        every { it.commit(any<TransactionStatus>()) } returns Unit
        every { it.rollback(any<TransactionStatus>()) } returns Unit
    }

    private lateinit var repository: FakeCreditRepository
    private lateinit var service: CreditService

    @BeforeEach
    fun setUp() {
        repository = FakeCreditRepository()
        service = CreditService(repository, mockk<ApplicationEventPublisher>(relaxed = true), transactionManager)
    }

    private fun givenCredits(free: Int, freeMonthly: Int = 30, vararg packages: Pair<Int, Long>) {
        repository.credit = AiCredit(
            id = 1,
            userId = userId,
            balance = free + packages.sumOf { it.first },
            freeMonthly = freeMonthly,
            freeRemaining = free,
            freeResetDate = LocalDate.now().plusMonths(1),
        )
        packages.forEachIndexed { index, (remaining, expiresInDays) ->
            repository.purchased += AiPurchasedCredit(
                id = (index + 1).toLong(),
                userId = userId,
                packageName = "PKG_$index",
                totalCredits = remaining,
                remaining = remaining,
                price = 1000,
                purchasedAt = LocalDateTime.now().minusDays(index.toLong()),
                expiresAt = LocalDateTime.now().plusDays(expiresInDays),
            )
        }
    }

    private fun pkg(id: Long) = repository.purchased.single { it.id == id }

    // ── 무료만 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("무료만 차감하면 무료로만 돌아온다")
    fun freeOnlyRoundTrip() {
        givenCredits(free = 10)

        val allocation = service.validateAndDeduct(userId, 5, "TEST")
        assertEquals(5, allocation.freeAmount)
        assertTrue(allocation.purchasedPortions.isEmpty())
        assertEquals(5, repository.credit!!.freeRemaining)

        service.refundAllocation(allocation)

        assertEquals(10, repository.credit!!.freeRemaining)
    }

    // ── 구매분만 (결함 (a)) ──────────────────────────────────────────────────

    /**
     * **결함 (a).** 예전에는 `freeRemaining += 5` 라서 구매분 5 가 무료분으로 바뀌었다.
     * 무료분은 월말에 리셋되므로 고객은 산 것을 잃는다.
     */
    @Test
    @DisplayName("무료가 0이면 구매 패키지로 되돌아간다 — 무료분으로 바뀌지 않는다")
    fun purchasedOnlyReturnsToThePackage() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))

        val allocation = service.validateAndDeduct(userId, 5, "TEST")
        assertEquals(0, allocation.freeAmount)
        assertEquals(listOf(CreditAllocation.PurchasedPortion(1L, 5)), allocation.purchasedPortions)
        assertEquals(95, pkg(1).remaining)

        service.refundAllocation(allocation)

        assertEquals(100, pkg(1).remaining, "구매 패키지로 돌아오지 않았다")
        assertEquals(0, repository.credit!!.freeRemaining, "구매분이 무료분으로 바뀌었다")
    }

    // ── 혼합 (결함 (b)) ──────────────────────────────────────────────────────

    /**
     * **결함 (b).** 무료 2 + 구매 3 을 썼는데 예전에는 `freeRemaining + 5` 를 `freeMonthly`
     * 로 clamp 해서 2 만 돌아오고 **구매분 3 이 증발**했다.
     */
    @Test
    @DisplayName("무료+구매 혼합 차감은 각각 제자리로 돌아온다")
    fun mixedDeductionRestoresBothSources() {
        givenCredits(free = 2, freeMonthly = 2, packages = arrayOf(100 to 30L))

        val allocation = service.validateAndDeduct(userId, 5, "TEST")
        assertEquals(2, allocation.freeAmount)
        assertEquals(listOf(CreditAllocation.PurchasedPortion(1L, 3)), allocation.purchasedPortions)
        assertEquals(0, repository.credit!!.freeRemaining)
        assertEquals(97, pkg(1).remaining)

        service.refundAllocation(allocation)

        assertEquals(2, repository.credit!!.freeRemaining)
        assertEquals(100, pkg(1).remaining, "구매분 3 이 사라졌다")
    }

    // ── 다중 패키지 FIFO ─────────────────────────────────────────────────────

    /**
     * 차감은 만료 임박순이다. 환불도 **가져온 그 패키지**로 각각 돌아가야 한다.
     * 총합만 맞추면 만료가 다른 패키지 사이에서 유효기간이 바뀐다.
     */
    @Test
    @DisplayName("여러 패키지에 걸친 차감은 패키지별로 정확히 복구된다")
    fun multiPackageFifoRestoresEachPackage() {
        // id=1 이 7일 뒤 만료(먼저 씀), id=2 가 60일 뒤 만료
        givenCredits(free = 0, packages = arrayOf(10 to 7L, 50 to 60L))

        val allocation = service.validateAndDeduct(userId, 25, "TEST")

        assertEquals(
            listOf(
                CreditAllocation.PurchasedPortion(1L, 10),
                CreditAllocation.PurchasedPortion(2L, 15),
            ),
            allocation.purchasedPortions,
            "만료 임박순 FIFO 가 아니다",
        )
        assertEquals(0, pkg(1).remaining)
        assertEquals("EXHAUSTED", pkg(1).status)
        assertEquals(35, pkg(2).remaining)

        service.refundAllocation(allocation)

        assertEquals(10, pkg(1).remaining)
        assertEquals(50, pkg(2).remaining)
        // 소진으로 EXHAUSTED 가 된 패키지는 되살아나야 다시 쓸 수 있다.
        assertEquals("ACTIVE", pkg(1).status, "복구했는데 EXHAUSTED 로 남아 쓸 수 없다")
    }

    /**
     * 소진된 패키지는 `findActivePurchasedCredits*` 에 잡히지 않는다. 활성 목록으로만
     * 되돌리려 하면 **방금 다 쓴 패키지에는 환불할 수 없다.** id 로 잠가야 하는 이유다.
     */
    @Test
    @DisplayName("소진되어 EXHAUSTED 가 된 패키지에도 환불된다")
    fun exhaustedPackageIsStillRefundable() {
        givenCredits(free = 0, packages = arrayOf(5 to 30L))

        val allocation = service.validateAndDeduct(userId, 5, "TEST")
        assertEquals("EXHAUSTED", pkg(1).status)
        assertTrue(repository.findActivePurchasedCredits(userId).isEmpty())

        service.refundAllocation(allocation)

        assertEquals(5, pkg(1).remaining)
        assertEquals("ACTIVE", pkg(1).status)
    }

    // ── withCredits ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("withCredits 성공이면 환불하지 않는다")
    fun withCreditsSuccessKeepsTheCharge() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))

        val result = service.withCredits(userId, AiFeature.META_GENERATION) { "ok" }

        assertEquals("ok", result)
        assertEquals(100 - AiFeature.META_GENERATION.creditCost, pkg(1).remaining)
    }

    @Test
    @DisplayName("withCredits 실패는 구매 패키지로 되돌린다")
    fun withCreditsFailureRestoresThePackage() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))

        assertFailsWith<IllegalStateException> {
            service.withCredits(userId, AiFeature.META_GENERATION) { error("모델 실패") }
        }

        assertEquals(100, pkg(1).remaining, "구매 패키지로 돌아오지 않았다")
        assertEquals(0, repository.credit!!.freeRemaining, "구매분이 무료분으로 바뀌었다")
    }

    @Test
    @DisplayName("잔액이 부족하면 아무것도 바뀌지 않는다")
    fun insufficientCreditsChangeNothing() {
        givenCredits(free = 1, packages = arrayOf(2 to 30L))

        assertFailsWith<InsufficientCreditException> { service.validateAndDeduct(userId, 10, "TEST") }

        assertEquals(1, repository.credit!!.freeRemaining)
        assertEquals(2, pkg(1).remaining)
        assertTrue(repository.transactions.isEmpty(), "차감하지 않았는데 원장에 기록했다")
    }

    // ── 부분 환불 (번역 언어별 / 파이프라인 단계별) ──────────────────────────

    /**
     * 번역은 합산 차감하고 **언어별로** 환불한다. 한 언어만 실패하면 그 몫만 돌아오고
     * 나머지는 청구된 채로 남아야 한다.
     */
    @Test
    @DisplayName("부분 환불은 요청한 몫만 되돌린다")
    fun partialRefundReturnsOnlyTheRequestedPortion() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 9, "TRANSLATION") // 3개 언어 × 3

        service.refundAllocation(allocation, 3) // 한 언어 실패

        assertEquals(94, pkg(1).remaining, "성공한 두 언어분까지 돌려줬다")
        assertEquals(6, allocation.refundableRemaining)
    }

    @Test
    @DisplayName("모든 언어가 실패하면 차감 전액이 제자리로 돌아온다")
    fun allPartialRefundsSumToTheFullDeduction() {
        givenCredits(free = 2, freeMonthly = 2, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 9, "TRANSLATION")

        repeat(3) { service.refundAllocation(allocation, 3) }

        assertEquals(2, repository.credit!!.freeRemaining)
        assertEquals(100, pkg(1).remaining)
        assertEquals(0, allocation.refundableRemaining)
    }

    /** 파이프라인 취소는 차감액 일부만 돌려준다. 나머지는 이미 쓴 단계 값이다. */
    @Test
    @DisplayName("단계별 부분 환불 후 남은 몫은 청구된 채로 유지된다")
    fun stageLevelPartialRefundKeepsTheConsumedPortion() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 20, "AI_PIPELINE")

        service.refundAllocation(allocation, 8) // 남은 단계분만 환불

        assertEquals(88, pkg(1).remaining)
        assertEquals(12, allocation.refundableRemaining)
    }

    // ── 중복·동시 환불 방어 ──────────────────────────────────────────────────

    /**
     * 같은 영수증으로 두 번 환불하면 없던 크레딧이 생긴다. 두 번째는 남은 금액이 없어
     * **아무 일도 하지 않아야** 한다. 예외로 만들지 않는 이유: 환불 경로는 대개 이미
     * 실패를 처리하는 중이라, 여기서 던지면 원래 오류를 가린다.
     */
    @Test
    @DisplayName("같은 영수증으로 두 번 환불해도 한 번만 반영된다")
    fun doubleRefundIsIgnored() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 5, "TEST")

        service.refundAllocation(allocation)
        service.refundAllocation(allocation)

        assertEquals(100, pkg(1).remaining, "두 번 환불되어 크레딧이 생겼다")
        assertEquals(1, repository.transactions.count { it.type.name == "REFUND" })
    }

    @Test
    @DisplayName("요청 금액이 남은 몫보다 커도 차감액을 넘겨 돌려주지 않는다")
    fun refundNeverExceedsTheDeduction() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 5, "TEST")

        service.refundAllocation(allocation, 999)

        assertEquals(100, pkg(1).remaining)
    }

    /**
     * 번역은 언어마다 **별도 virtual thread** 에서 환불한다. 영수증의 남은 금액 계산이
     * 동기화되지 않으면 두 스레드가 같은 몫을 떼어가 총합이 차감액을 넘는다.
     *
     * ## 여기서 검사하는 것과 하지 않는 것
     *
     * 검사하는 것은 **영수증의 carve-out 이 원자적인가**다. 그것이 이번에 도입한
     * 공유 가변 상태이고, 깨지면 차감하지 않은 크레딧이 생긴다.
     *
     * 행 상태(`pkg.remaining`)는 여기서 단언하지 않는다. `applyRefund` 는 패키지 행을
     * 읽고-고쳐-쓰는데, 운영에서는 [CreditRepository.findPurchasedCreditsByIdsForUpdate]
     * 의 `FOR UPDATE` 와 트랜잭션이 그 구간을 직렬화한다. 이 테스트의 인메모리 저장소에는
     * 행 잠금이 없어 마지막 쓰기가 이긴다 — **저장소 대역의 한계이지 운영 결함이 아니다.**
     * 실제 행 잠금은 Testcontainers 통합 테스트가 필요하고, 이번 세션에서는 Docker 가
     * 내려가 있어 확인하지 못했다.
     */
    @Test
    @DisplayName("동시에 부분 환불해도 영수증이 차감액보다 많이 내주지 않는다")
    fun concurrentPartialRefundsNeverOverRefund() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 9, "TRANSLATION")

        val granted = java.util.concurrent.atomic.AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(6)
        repeat(6) {
            Thread.startVirtualThread {
                start.await()
                // 서비스가 아니라 영수증을 직접 두드린다. 저장소 대역의 경합을 섞지 않고
                // carve-out 자체의 원자성만 본다.
                allocation.takeForRefund(3)?.let { granted.addAndGet(it.total) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "환불 스레드가 끝나지 않았다")

        // 9 만 차감했으므로 6 회 × 3 을 요청해도 9 만 내줘야 한다.
        assertEquals(9, granted.get(), "차감액보다 많이 내줬다 — 없던 크레딧이 생긴다")
        assertEquals(0, allocation.refundableRemaining)
    }

    // ── DB 실패 시 영수증 복구 ───────────────────────────────────────────────

    /**
     * **떼어냈는데 DB 반영이 실패하면 영수증이 소진된 채로 남는다.**
     *
     * [CreditAllocation.takeForRefund] 는 떼어내는 순간 남은 금액을 줄인다. 그 뒤 저장이
     * 실패하면 크레딧은 안 돌아갔는데 영수증만 비어 **재시도가 영구히 막힌다** — 고객은
     * 결과도 크레딧도 잃는다. 실패 경로가 반드시 원상복구해야 한다.
     */
    @Test
    @DisplayName("환불 저장이 실패하면 영수증을 복원해 재시도할 수 있다")
    fun failedRefundRestoresTheReceipt() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 5, "TEST")
        repository.failNextPurchasedUpdate = true

        assertFailsWith<IllegalStateException> { service.refundAllocation(allocation) }

        // 소진된 채로 남으면 5 가 영원히 돌아가지 못한다.
        assertEquals(5, allocation.refundableRemaining, "영수증이 소진되어 재시도할 수 없다")

        // 재시도가 실제로 성공해야 의미가 있다.
        service.refundAllocation(allocation)
        assertEquals(100, pkg(1).remaining)
    }

    @Test
    @DisplayName("부분 환불 저장이 실패해도 그 몫만 복원된다")
    fun failedPartialRefundRestoresOnlyThatPortion() {
        givenCredits(free = 0, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 9, "TRANSLATION")

        service.refundAllocation(allocation, 3)
        assertEquals(6, allocation.refundableRemaining)

        repository.failNextPurchasedUpdate = true
        assertFailsWith<IllegalStateException> { service.refundAllocation(allocation, 3) }

        assertEquals(6, allocation.refundableRemaining, "실패한 몫이 복원되지 않았다")
        assertEquals(94, pkg(1).remaining, "실패했는데 반영됐다")
    }

    // ── 영속 스냅샷 복원 ─────────────────────────────────────────────────────

    /**
     * 파이프라인 정산은 다른 요청에서 실행되므로 차감 당시 객체를 가질 수 없다.
     * 저장해 둔 분해로 복원한 영수증도 **패키지별로 정확히** 되돌려야 한다.
     */
    @Test
    @DisplayName("저장된 분해로 복원한 영수증도 패키지별 FIFO 로 복구한다")
    fun restoredAllocationRestoresPerPackage() {
        givenCredits(free = 0, packages = arrayOf(10 to 7L, 50 to 60L))
        service.validateAndDeduct(userId, 25, "AI_PIPELINE") // 원래 요청 (영수증은 버린다)
        assertEquals("EXHAUSTED", pkg(1).status)

        // 다른 요청에서 스냅샷만 들고 정산한다.
        val restored = CreditAllocation.restored(
            userId = userId,
            featureName = "AI_PIPELINE_CANCEL",
            freeAmount = 0,
            purchasedAmounts = mapOf(1L to 10, 2L to 15),
        )
        service.refundAllocation(restored, 25)

        assertEquals(10, pkg(1).remaining)
        assertEquals(50, pkg(2).remaining)
        assertEquals("ACTIVE", pkg(1).status, "소진 패키지가 되살아나지 않아 쓸 수 없다")
    }

    /** 미사용분만 돌려주는 부분 정산도 출처를 지킨다. */
    @Test
    @DisplayName("복원한 영수증의 부분 정산도 출처를 지킨다")
    fun restoredAllocationPartialRefundKeepsSources() {
        givenCredits(free = 0, packages = arrayOf(10 to 7L, 50 to 60L))
        service.validateAndDeduct(userId, 25, "AI_PIPELINE")

        val restored = CreditAllocation.restored(
            userId, "AI_PIPELINE_CANCEL", 0, mapOf(1L to 10, 2L to 15),
        )
        service.refundAllocation(restored, 12) // 미사용분만

        // FIFO 로 id=1 의 10 을 먼저, 남은 2 를 id=2 에서 되돌린다.
        assertEquals(10, pkg(1).remaining)
        assertEquals(37, pkg(2).remaining)
        assertEquals(0, repository.credit!!.freeRemaining, "구매분이 무료분으로 갔다")
    }

    // ── 무료 clamp 경계 ──────────────────────────────────────────────────────

    /**
     * 차감과 환불 사이에 월 리셋이 있었다면 무료분은 이미 채워져 있다. 그 위에 더하면
     * 없던 무료 크레딧이 생기므로 clamp 는 유지한다. 대신 **구매분은 clamp 와 무관하게**
     * 제자리로 돌아가야 한다 — 예전에는 이 경우 구매분이 통째로 사라졌다.
     */
    @Test
    @DisplayName("월 리셋으로 무료분이 찼어도 구매분은 그대로 복구된다")
    fun freeClampDoesNotSwallowThePurchasedPortion() {
        givenCredits(free = 2, freeMonthly = 2, packages = arrayOf(100 to 30L))
        val allocation = service.validateAndDeduct(userId, 5, "TEST")

        // 차감 뒤 월 리셋이 일어나 무료분이 한도까지 복구된 상황
        repository.credit = repository.credit!!.copy(freeRemaining = 2)

        service.refundAllocation(allocation)

        assertEquals(2, repository.credit!!.freeRemaining, "무료분이 한도를 넘었다")
        assertEquals(100, pkg(1).remaining, "clamp 때문에 구매분까지 사라졌다")
    }

    // ── 인메모리 저장소 ──────────────────────────────────────────────────────

    /**
     * 실제 행 상태를 검사하려고 쓴다. mock 으로 호출 인자만 보면 "복구했다고 주장했다"
     * 까지밖에 확인할 수 없고, 여러 번의 갱신이 누적되는 방식은 잡지 못한다.
     */
    private class FakeCreditRepository : CreditRepository {
        var credit: AiCredit? = null
        val purchased = mutableListOf<AiPurchasedCredit>()
        val transactions = mutableListOf<AiCreditTransaction>()

        override fun findByUserId(userId: Long) = credit
        override fun findByUserIdForUpdate(userId: Long) = credit
        override fun save(credit: AiCredit) = credit.also { this.credit = it }
        override fun update(credit: AiCredit) = credit.also { this.credit = it }
        override fun getTransactions(userId: Long, page: Int, size: Int) = transactions.toList()
        override fun countTransactions(userId: Long) = transactions.size.toLong()
        override fun saveTransaction(transaction: AiCreditTransaction) =
            transaction.also { transactions += it }

        override fun findActivePurchasedCredits(userId: Long) =
            purchased.filter { it.status == "ACTIVE" && it.expiresAt.isAfter(LocalDateTime.now()) }
                .sortedBy { it.expiresAt }

        override fun findActivePurchasedCreditsForUpdate(userId: Long) = findActivePurchasedCredits(userId)

        override fun findPurchasedCreditsByIdsForUpdate(userId: Long, ids: Collection<Long>) =
            purchased.filter { it.id in ids }.sortedBy { it.expiresAt }

        override fun savePurchasedCredit(credit: AiPurchasedCredit) =
            credit.also { purchased += it }

        /** 다음 구매 크레딧 갱신을 한 번 실패시킨다. DB 장애 재현용. */
        var failNextPurchasedUpdate = false

        override fun updatePurchasedCredit(credit: AiPurchasedCredit): AiPurchasedCredit {
            if (failNextPurchasedUpdate) {
                failNextPurchasedUpdate = false
                throw IllegalStateException("구매 크레딧 갱신 실패")
            }
            val index = purchased.indexOfFirst { it.id == credit.id }
            if (index >= 0) purchased[index] = credit else purchased += credit
            return credit
        }

        override fun findExpiredCredits() = emptyList<AiPurchasedCredit>()
        override fun findUsersForFreeReset(today: LocalDate) = emptyList<Long>()
        override fun bulkExpirePurchasedCredits() = 0
        override fun findUsersWithExpiredCredits() = emptyList<Long>()
        override fun findLowCreditUsers() = emptyList<AiCredit>()
    }
}
