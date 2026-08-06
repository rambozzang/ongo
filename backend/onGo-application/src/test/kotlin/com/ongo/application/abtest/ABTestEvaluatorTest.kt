package com.ongo.application.abtest

import com.ongo.application.abtest.dto.ABTestStatisticsResponse
import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.event.ABTestCompletedEvent
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

/**
 * 스케줄 배치의 **항목별 격리**를 검증한다.
 *
 * 이 배치는 `@Profile("wip")` 이 아니다. `@Component` + `@Scheduled` 라 기본 프로필에서
 * 매시간 실제로 돈다. 예전에는 메서드 전체가 `@Transactional` 이고 항목별로 예외를 삼켜서,
 * 한 건의 DB 오류가 트랜잭션을 abort 시키면 이후 항목이 전부 실패하고 이미 성공한 종료
 * 처리까지 롤백됐다. `CreditScheduler` 와 같은 결함이었다.
 *
 * 실제 트랜잭션 의미는 `SpringTransactionParticipationIT` 가 PostgreSQL 로 고정한다.
 * 여기서는 한 건이 실패해도 나머지가 계속 처리되는지를 본다.
 */
@ExtendWith(MockKExtension::class)
class ABTestEvaluatorTest {

    @MockK
    private lateinit var abTestRepository: ABTestRepository

    @MockK
    private lateinit var variantRepository: ABTestVariantRepository

    @MockK
    private lateinit var statisticsService: ABTestStatisticsService

    @MockK(relaxed = true)
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var evaluator: ABTestEvaluator

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // 항목별 트랜잭션 경계만 흉내 낸다. 실제 커밋/롤백은 IT 가 검증한다.
        val txManager = mockk<PlatformTransactionManager>()
        every { txManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { txManager.commit(any<TransactionStatus>()) } returns Unit
        every { txManager.rollback(any<TransactionStatus>()) } returns Unit

        evaluator = ABTestEvaluator(
            abTestRepository = abTestRepository,
            variantRepository = variantRepository,
            statisticsService = statisticsService,
            eventPublisher = eventPublisher,
            transactionManager = txManager,
        )
    }

    private fun test(id: Long) = ABTest(id = id, userId = 1L, testName = "테스트 $id", status = "RUNNING")

    private fun significant(testId: Long, winner: Long) = ABTestStatisticsResponse(
        testId = testId,
        sampleSizeRequired = 100,
        currentSampleSize = 200,
        sampleProgress = 1.0,
        confidence = 99.0,
        pValue = 0.01,
        isSignificant = true,
        winnerVariantId = winner,
        variants = emptyList(),
    )

    @Test
    @DisplayName("한 건이 실패해도 나머지 테스트는 계속 평가된다")
    fun failureOnOneTestDoesNotStopTheRest() {
        every { abTestRepository.findByStatus("RUNNING") } returns listOf(test(1), test(2), test(3))
        every { statisticsService.getStatistics(1L, 1L) } returns significant(1L, 11L)
        // 2번에서 DB 오류가 난다. 예전 구조라면 여기서 트랜잭션이 죽어 3번도 못 돌았다
        every { statisticsService.getStatistics(1L, 2L) } throws IllegalStateException("DB 오류")
        every { statisticsService.getStatistics(1L, 3L) } returns significant(3L, 33L)
        every { abTestRepository.update(any()) } answers { firstArg() }

        evaluator.evaluateRunningTests()

        // 1번과 3번은 종료 처리됐다
        verify(exactly = 1) { abTestRepository.update(match { it.id == 1L && it.status == "COMPLETED" }) }
        verify(exactly = 1) { abTestRepository.update(match { it.id == 3L && it.status == "COMPLETED" }) }
        // 2번은 갱신되지 않았다
        verify(exactly = 0) { abTestRepository.update(match { it.id == 2L }) }
    }

    @Test
    @DisplayName("유의미하지 않은 테스트는 종료하지 않는다")
    fun insignificantTestIsNotCompleted() {
        every { abTestRepository.findByStatus("RUNNING") } returns listOf(test(1))
        every { statisticsService.getStatistics(1L, 1L) } returns
            significant(1L, 11L).copy(isSignificant = false, winnerVariantId = null)

        evaluator.evaluateRunningTests()

        verify(exactly = 0) { abTestRepository.update(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<ABTestCompletedEvent>()) }
    }

    @Test
    @DisplayName("실행 중인 테스트 조회가 실패해도 예외를 던지지 않는다 — 스케줄러가 죽으면 안 된다")
    fun findFailureDoesNotPropagate() {
        every { abTestRepository.findByStatus("RUNNING") } throws IllegalStateException("조회 실패")

        evaluator.evaluateRunningTests()

        verify(exactly = 0) { abTestRepository.update(any()) }
    }

    @Test
    @DisplayName("유의미한 테스트는 종료 처리하고 완료 이벤트를 발행한다")
    fun significantTestIsCompletedAndPublished() {
        every { abTestRepository.findByStatus("RUNNING") } returns listOf(test(7))
        every { statisticsService.getStatistics(1L, 7L) } returns significant(7L, 77L)
        every { abTestRepository.update(any()) } answers { firstArg() }

        evaluator.evaluateRunningTests()

        verify(exactly = 1) {
            abTestRepository.update(match { it.id == 7L && it.winnerVariantId == 77L && it.endedAt != null })
        }
        verify(exactly = 1) { eventPublisher.publishEvent(any<ABTestCompletedEvent>()) }
    }
}
