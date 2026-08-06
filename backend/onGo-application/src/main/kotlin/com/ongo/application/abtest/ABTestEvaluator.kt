package com.ongo.application.abtest

import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.event.ABTestCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Component
class ABTestEvaluator(
    private val abTestRepository: ABTestRepository,
    private val variantRepository: ABTestVariantRepository,
    private val statisticsService: ABTestStatisticsService,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(ABTestEvaluator::class.java)

    /**
     * 테스트 1건을 독립 트랜잭션으로 묶는다.
     *
     * 같은 클래스 안에서 `@Transactional` 메서드를 자기호출하면 프록시를 타지 않아
     * 전파 설정이 무시된다. 그래서 애노테이션 대신 [TransactionTemplate]을 쓴다.
     */
    private val perItemTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    // 바깥 루프에는 트랜잭션을 두지 않는다. 테스트 1건씩 REQUIRES_NEW 로 묶고 루프 바깥에서 잡는다.
    //
    // 예전에는 이 메서드에 @Transactional 이 붙어 전체 테스트가 한 트랜잭션이었고
    // 항목별로 catch 해서 계속 진행했다. jOOQ 가 스프링 트랜잭션에 참여하게 되면서
    // 한 건의 DB 오류가 트랜잭션 전체를 abort 시키고, 이후 항목이 전부 실패하며,
    // 이미 성공한 종료 처리까지 롤백되는 구조가 됐다.
    // CreditScheduler 와 같은 결함이며 같은 방식으로 고친다.
    @Scheduled(fixedRate = 3600000) // every hour
    fun evaluateRunningTests() {
        val runningTests = findRunningTests()
        log.info("A/B 테스트 자동 평가 시작. job=abTestEvaluate targets={}", runningTests.size)

        val failed = mutableListOf<Long?>()
        var completedCount = 0
        for (test in runningTests) {
            try {
                if (perItemTx.execute { evaluateOne(test) } == true) completedCount++
            } catch (e: Exception) {
                failed += test.id
                log.error("A/B 테스트 평가 실패. job=abTestEvaluate testId={}", test.id, e)
            }
        }

        if (failed.isEmpty()) {
            log.info(
                "A/B 테스트 자동 평가 완료. job=abTestEvaluate completed={} total={} outcome={}",
                completedCount, runningTests.size, OUTCOME_OK,
            )
        } else {
            log.error(
                "A/B 테스트 자동 평가 일부 실패. job=abTestEvaluate completed={} failed={} total={} outcome={} failedTestIds={}",
                completedCount, failed.size, runningTests.size, OUTCOME_PARTIAL_FAILURE, failed,
            )
        }
    }

    /**
     * 테스트 1건 평가. [perItemTx] 안에서만 호출한다.
     *
     * @return 유의미한 결과가 나와 실제로 종료 처리했으면 true
     */
    private fun evaluateOne(test: ABTest): Boolean {
        val statistics = statisticsService.getStatistics(test.userId, test.id!!)
        if (!statistics.isSignificant || statistics.winnerVariantId == null) return false

        log.info("A/B 테스트 자동 종료. testId={} winnerVariantId={}", test.id, statistics.winnerVariantId)

        abTestRepository.update(
            test.copy(
                status = "COMPLETED",
                winnerVariantId = statistics.winnerVariantId,
                endedAt = LocalDateTime.now(),
            )
        )

        eventPublisher.publishEvent(
            ABTestCompletedEvent(
                testId = test.id!!,
                userId = test.userId,
                winnerVariantId = statistics.winnerVariantId,
            )
        )
        return true
    }

    private fun findRunningTests() = try {
        abTestRepository.findByStatus("RUNNING")
    } catch (e: Exception) {
        log.error("실행 중인 테스트 조회 실패. job=abTestEvaluate", e)
        emptyList()
    }

    companion object {
        private const val OUTCOME_OK = "OK"
        private const val OUTCOME_PARTIAL_FAILURE = "PARTIAL_FAILURE"
    }
}
