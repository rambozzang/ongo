package com.ongo.application.schedule

import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.event.ScheduleFailedEvent
import com.ongo.domain.event.VideoPublishEvent
import com.ongo.domain.schedule.ScheduleRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class ScheduleExecutor(
    private val scheduleRepository: ScheduleRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val MAX_RETRY_COUNT = 3
    }

    @Scheduled(fixedRate = 60000) // 1분마다 체크
    @Transactional
    fun executeDueSchedules() {
        val now = LocalDateTime.now(KST)
        val dueSchedules = scheduleRepository.findDueSchedules(now)

        if (dueSchedules.isEmpty()) return
        log.info("실행할 예약 ${dueSchedules.size}건 발견")

        dueSchedules.forEach { schedule ->
            try {
                // 동시성 보호: 상태를 PROCESSING으로 먼저 변경
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.PROCESSING))

                val platforms = schedule.platforms.keys.map { safeValueOfOrThrow<Platform>(it) }
                eventPublisher.publishEvent(
                    VideoPublishEvent(
                        videoId = schedule.videoId,
                        userId = schedule.userId,
                        targetPlatforms = platforms,
                        immediate = true
                    )
                )
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.PUBLISHED))
                log.info("예약 실행 완료 [scheduleId=${schedule.id}, videoId=${schedule.videoId}]")
            } catch (e: Exception) {
                log.error("예약 실행 실패 [scheduleId=${schedule.id}]: ${e.message}", e)
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                // 실패 시 알림 이벤트 발행
                eventPublisher.publishEvent(
                    ScheduleFailedEvent(
                        scheduleId = schedule.id!!,
                        videoId = schedule.videoId,
                        userId = schedule.userId,
                        reason = e.message ?: "알 수 없는 오류"
                    )
                )
            }
        }
    }
}
