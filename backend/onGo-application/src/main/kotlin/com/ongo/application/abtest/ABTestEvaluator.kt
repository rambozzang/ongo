package com.ongo.application.abtest

import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.event.ABTestCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class ABTestEvaluator(
    private val abTestRepository: ABTestRepository,
    private val variantRepository: ABTestVariantRepository,
    private val statisticsService: ABTestStatisticsService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(ABTestEvaluator::class.java)

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    fun evaluateRunningTests() {
        log.info("A/B 테스트 자동 평가 시작")

        // Find all tests with RUNNING status
        // We query all users' tests with status RUNNING
        val runningTests = findRunningTests()

        for (test in runningTests) {
            try {
                val statistics = statisticsService.getStatistics(test.userId, test.id!!)

                if (statistics.isSignificant && statistics.winnerVariantId != null) {
                    log.info("A/B 테스트 #{} 자동 종료: 우승 variant={}", test.id, statistics.winnerVariantId)

                    val updated = test.copy(
                        status = "COMPLETED",
                        winnerVariantId = statistics.winnerVariantId,
                        endedAt = LocalDateTime.now(),
                    )
                    abTestRepository.update(updated)

                    eventPublisher.publishEvent(
                        ABTestCompletedEvent(
                            testId = test.id!!,
                            userId = test.userId,
                            winnerVariantId = statistics.winnerVariantId,
                        )
                    )
                }
            } catch (e: Exception) {
                log.error("A/B 테스트 #{} 평가 중 오류", test.id, e)
            }
        }

        log.info("A/B 테스트 자동 평가 완료")
    }

    private fun findRunningTests() = try {
        abTestRepository.findByStatus("RUNNING")
    } catch (e: Exception) {
        log.error("실행 중인 테스트 조회 실패", e)
        emptyList()
    }
}
