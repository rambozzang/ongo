package com.ongo.application.subscription

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
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
    private val notificationRepository: NotificationRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    fun processBilling() {
        log.info("빌링 처리 시작")
        val now = LocalDateTime.now()

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

        log.info("빌링 처리 완료")
    }
}
