package com.ongo.application.subscription

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.common.enums.NotificationType
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class BillingScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val creditRepository: CreditRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    fun processBilling() {
        log.info("빌링 처리 시작")
        val now = LocalDateTime.now()

        // 트라이얼 만료 처리
        val expiredTrials = subscriptionRepository.findTrialExpired(now)
        expiredTrials.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0,
                updatedAt = now,
            ))
            val user = userRepository.findById(sub.userId)
            if (user != null) {
                userRepository.update(user.copy(planType = PlanType.FREE))
            }
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "트라이얼 만료",
                message = "7일 무료 체험이 종료되어 Free 플랜으로 전환되었습니다.",
            ))
            log.info("트라이얼 만료 → Free 전환: userId={}", sub.userId)
        }

        // 일시정지 자동 재개 (30일 초과)
        val pausedToResume = subscriptionRepository.findPausedToResume(now)
        pausedToResume.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                status = SubscriptionStatus.ACTIVE,
                pausedAt = null,
                resumeAt = null,
                updatedAt = now,
            ))
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "구독 자동 재개",
                message = "일시정지 기간(30일)이 만료되어 구독이 자동으로 재개되었습니다.",
            ))
            log.info("일시정지 자동 재개: userId={}", sub.userId)
        }

        // 미결제 3일 유예 → 알림
        val pastDue3 = subscriptionRepository.findPastDue(3)
        pastDue3.forEach { sub ->
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "결제 실패 알림",
                message = "결제가 실패했습니다. 3일 이내에 결제 수단을 확인해주세요."
            ))
            subscriptionRepository.update(sub.copy(status = SubscriptionStatus.PAST_DUE))
        }

        // 미결제 7일 → Free 전환
        val pastDue7 = subscriptionRepository.findPastDue(7)
        pastDue7.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0,
                updatedAt = now
            ))
            val user = userRepository.findById(sub.userId)
            if (user != null) {
                userRepository.update(user.copy(planType = PlanType.FREE))
            }
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "구독 만료",
                message = "결제 미처리로 Free 플랜으로 전환되었습니다."
            ))
            log.info("Free 전환: userId=${sub.userId}")
        }

        // 취소된 구독 중 기간 종료된 것 → Free 전환
        val cancelledExpired = subscriptionRepository.findDueForBilling(now)
            .filter { it.status == SubscriptionStatus.CANCELLED && it.currentPeriodEnd?.isBefore(now) == true }
        cancelledExpired.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0
            ))
            val user = userRepository.findById(sub.userId)
            if (user != null) {
                userRepository.update(user.copy(planType = PlanType.FREE))
            }
        }

        // 다운그레이드 예약 적용: pendingPlanType 설정 + 기간 만료된 구독
        val pendingDowngrades = subscriptionRepository.findWithPendingPlanType()
            .filter { it.currentPeriodEnd?.isBefore(now) == true }
        pendingDowngrades.forEach { sub ->
            val newPlan = sub.pendingPlanType ?: return@forEach
            subscriptionRepository.update(sub.copy(
                planType = newPlan,
                price = if (sub.billingCycle == BillingCycle.YEARLY) newPlan.yearlyPrice else newPlan.price,
                pendingPlanType = null,
                status = if (newPlan == PlanType.FREE) SubscriptionStatus.FREE else SubscriptionStatus.ACTIVE,
                updatedAt = now
            ))
            val user = userRepository.findById(sub.userId)
            if (user != null) {
                userRepository.update(user.copy(planType = newPlan))
            }
            // 크레딧 월간 한도 조정
            val credit = creditRepository.findByUserId(sub.userId)
            if (credit != null) {
                creditRepository.update(credit.copy(
                    freeMonthly = newPlan.freeCredits,
                    updatedAt = java.time.LocalDateTime.now()
                ))
            }
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "플랜 변경 완료",
                message = "${sub.planType.displayName}에서 ${newPlan.displayName}으로 플랜이 변경되었습니다."
            ))
            log.info("다운그레이드 적용: userId=${sub.userId}, ${sub.planType} → $newPlan")
        }

        log.info("빌링 처리 완료")
    }
}
