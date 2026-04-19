package com.ongo.application.revenue

import com.ongo.common.enums.NotificationType
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.revenue.RevenueAlertConfigRepository
import com.ongo.domain.revenue.RevenueAlertType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RevenueAlertScheduler(
    private val revenueAlertConfigRepository: RevenueAlertConfigRepository,
    private val revenueUseCase: RevenueUseCase,
    private val notificationRepository: NotificationRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매일 오전 9시 — DAILY_SUMMARY 알림 발송
     * 전일(1일) 수익 요약을 활성화된 사용자에게 알림
     */
    @Scheduled(cron = "0 0 9 * * *")
    fun sendDailyRevenueSummary() {
        log.info("일일 수익 요약 알림 발송 시작")

        val configs = revenueAlertConfigRepository.findAllEnabledByAlertType(
            RevenueAlertType.DAILY_SUMMARY.name
        )

        configs.forEach { config ->
            runCatching {
                val summary = revenueUseCase.getRevenueSummary(config.userId, 1)
                val revenueKrw = summary.totalRevenueKrw
                val growthPercent = summary.growthPercent

                val message = if (growthPercent >= 0) {
                    "전일 수익: ${revenueKrw}원 (전일 대비 +${growthPercent}%)"
                } else {
                    "전일 수익: ${revenueKrw}원 (전일 대비 ${growthPercent}%)"
                }

                notificationRepository.save(
                    Notification(
                        userId = config.userId,
                        type = NotificationType.REVENUE_ALERT,
                        title = "일일 수익 요약",
                        message = message,
                        referenceType = "REVENUE_ALERT",
                    )
                )
                log.info("일일 수익 요약 알림 발송: userId={}, revenue={}", config.userId, revenueKrw)
            }.onFailure { e ->
                log.error("일일 수익 요약 알림 실패: userId={}", config.userId, e)
            }
        }

        log.info("일일 수익 요약 알림 발송 완료: {}건", configs.size)
    }

    /**
     * 매일 오전 9시 — ANOMALY_DETECTION 알림 발송
     * 수익이 임계값 미만이면 이상 감지 알림 발송
     */
    @Scheduled(cron = "0 5 9 * * *")
    fun detectRevenueAnomaly() {
        log.info("수익 이상 감지 알림 발송 시작")

        val configs = revenueAlertConfigRepository.findAllEnabledByAlertType(
            RevenueAlertType.ANOMALY_DETECTION.name
        )

        configs.forEach { config ->
            runCatching {
                val threshold = config.thresholdValue?.toLong() ?: return@runCatching
                val summary = revenueUseCase.getRevenueSummary(config.userId, 1)
                val revenueKrw = summary.totalRevenueKrw

                if (revenueKrw < threshold) {
                    notificationRepository.save(
                        Notification(
                            userId = config.userId,
                            type = NotificationType.REVENUE_ALERT,
                            title = "수익 이상 감지",
                            message = "전일 수익(${revenueKrw}원)이 설정한 임계값(${threshold}원) 미만입니다.",
                            referenceType = "REVENUE_ANOMALY",
                        )
                    )
                    log.info("수익 이상 감지 알림: userId={}, revenue={}, threshold={}", config.userId, revenueKrw, threshold)
                }
            }.onFailure { e ->
                log.error("수익 이상 감지 알림 실패: userId={}", config.userId, e)
            }
        }

        log.info("수익 이상 감지 알림 완료: {}건 검사", configs.size)
    }
}
