package com.ongo.application.ai

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.canWrite
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class WeeklyDigestScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val weeklyDigestUseCase: WeeklyDigestUseCase,
    private val userWriteGuard: UserWriteGuard,
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

        var frozenCount = 0
        var insufficientCreditCount = 0
        var rateLimitedCount = 0

        for (subscription in allSubscriptions) {
            // 사전 검사. 동결된 계정은 AI 호출까지 가지 않고 거른다.
            // 실제 안전은 유스케이스가 저장 직전에 다시 확인한다 — AI 호출이 길어서
            // 여기서 통과했더라도 그 사이 탈퇴 요청이 들어올 수 있다.
            val writable = userWriteGuard.canWrite(subscription.userId) {
                log.info("동결된 계정이라 주간 다이제스트를 건너뛴다. userId={}", subscription.userId)
            }
            if (!writable) {
                frozenCount++
                continue
            }

            try {
                weeklyDigestUseCase.generateDigest(subscription.userId, weekStart, weekEnd)
                successCount++
                log.debug("주간 다이제스트 생성 완료: userId={}", subscription.userId)
            } catch (e: InsufficientCreditException) {
                // 잔액 부족은 장애가 아니라 정상적인 사용자 상태다. ERROR 로 남기면 매주
                // 크레딧을 다 쓴 사용자 수만큼 경보가 울려 진짜 AI 장애를 덮는다.
                // 유스케이스가 차감 전에 던지므로 AI 호출도 저장도 일어나지 않았다.
                insufficientCreditCount++
                log.info(
                    "크레딧이 부족해 주간 다이제스트를 건너뛴다. userId={} 필요={} 잔여={}",
                    subscription.userId, e.required, e.available,
                )
            } catch (e: AiRateLimitExceededException) {
                // 버킷은 모든 AI 기능이 공유한다. 같은 분에 대화형 AI 를 한도까지 쓴
                // 사용자의 예약 다이제스트가 여기 걸릴 수 있다 — 장애가 아니다.
                // 유스케이스가 차감 전에 던지므로 AI 호출도 저장도 일어나지 않았다.
                rateLimitedCount++
                log.info("AI 요청 한도로 주간 다이제스트를 건너뛴다. userId={}", subscription.userId)
            } catch (e: Exception) {
                failCount++
                log.error("주간 다이제스트 생성 실패: userId={}", subscription.userId, e)
            }
        }

        log.info(
            "주간 다이제스트 자동 생성 완료: 성공={}, 실패={}, 동결로 건너뜀={}, 크레딧 부족으로 건너뜀={}, 한도로 건너뜀={}",
            successCount, failCount, frozenCount, insufficientCreditCount, rateLimitedCount,
        )
    }
}
