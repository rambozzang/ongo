package com.ongo.application.ai

import com.ongo.common.enums.PlanType
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class WeeklyDigestScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val weeklyDigestUseCase: WeeklyDigestUseCase,
) {

    private val log = LoggerFactory.getLogger(WeeklyDigestScheduler::class.java)

    /**
     * 매주 월요일 09:00에 Pro/Business 사용자를 위한 주간 다이제스트를 자동 생성합니다.
     */
    @Scheduled(cron = "0 0 9 ? * MON")
    fun generateWeeklyDigests() {
        log.info("주간 다이제스트 자동 생성 시작")

        val today = LocalDate.now()
        val weekEnd = today.minusDays(1) // 일요일
        val weekStart = weekEnd.minusDays(6) // 월요일

        val eligiblePlans = setOf(PlanType.PRO, PlanType.BUSINESS)

        // Pro/Business 구독자의 userId 목록은 SubscriptionRepository에서 가져옴
        // findDueForBilling을 활용하기보다는 전체 활성 구독에서 필터링
        val allSubscriptions = eligiblePlans.flatMap { planType ->
            try {
                subscriptionRepository.findByPlanType(planType)
            } catch (e: Exception) {
                log.warn("구독 조회 실패 (plan={}): {}", planType, e.message)
                emptyList()
            }
        }

        var successCount = 0
        var failCount = 0

        for (subscription in allSubscriptions) {
            try {
                weeklyDigestUseCase.generateDigest(subscription.userId, weekStart, weekEnd)
                successCount++
                log.debug("주간 다이제스트 생성 완료: userId={}", subscription.userId)
            } catch (e: Exception) {
                failCount++
                log.error("주간 다이제스트 생성 실패: userId={}", subscription.userId, e)
            }
        }

        log.info("주간 다이제스트 자동 생성 완료: 성공={}, 실패={}", successCount, failCount)
    }
}
