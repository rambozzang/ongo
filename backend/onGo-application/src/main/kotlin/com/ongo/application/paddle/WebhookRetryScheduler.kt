package com.ongo.application.paddle

import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.sql.DataSource

@Component
class WebhookRetryScheduler(
    private val webhookEventRepository: WebhookEventRepository,
    private val paddleWebhookService: PaddleWebhookService,
    private val dataSource: DataSource,
) {

    private val log = LoggerFactory.getLogger(WebhookRetryScheduler::class.java)

    companion object {
        private const val LOCK_WEBHOOK_RETRY = 100010L
    }

    /**
     * 1분마다 실패한 웹훅 이벤트 재처리
     */
    @Scheduled(fixedDelay = 60_000)
    fun retryFailedWebhooks() {
        if (!tryAdvisoryLock(LOCK_WEBHOOK_RETRY)) {
            log.info("다른 인스턴스에서 웹훅 재처리 실행 중, 스킵")
            return
        }
        try {
            val now = LocalDateTime.now()
            val retryableEvents = webhookEventRepository.findRetryable(now)

            if (retryableEvents.isEmpty()) return

            log.info("웹훅 재처리 시작. 대상 이벤트 수: {}", retryableEvents.size)

            var successCount = 0
            var failCount = 0

            for (event in retryableEvents) {
                try {
                    paddleWebhookService.reprocessWebhookEvent(event)

                    webhookEventRepository.update(event.copy(
                        status = "PROCESSED",
                        processedAt = LocalDateTime.now(),
                    ))
                    successCount++
                    log.info("웹훅 재처리 성공: eventId={}, eventType={}", event.eventId, event.eventType)
                } catch (e: Exception) {
                    val newRetryCount = event.retryCount + 1

                    if (newRetryCount >= event.maxRetries) {
                        webhookEventRepository.update(event.copy(
                            status = "DEAD_LETTER",
                            retryCount = newRetryCount,
                            errorMessage = e.message?.take(500),
                        ))
                        log.error("웹훅 최대 재시도 초과, DEAD_LETTER 처리: eventId={}, retryCount={}",
                            event.eventId, newRetryCount, e)
                    } else {
                        val nextRetryAt = LocalDateTime.now().plusMinutes((1L shl minOf(newRetryCount, 5)).toLong())
                        webhookEventRepository.update(event.copy(
                            status = "FAILED",
                            retryCount = newRetryCount,
                            nextRetryAt = nextRetryAt,
                            errorMessage = e.message?.take(500),
                        ))
                        log.warn("웹훅 재처리 실패: eventId={}, retryCount={}/{}, nextRetryAt={}",
                            event.eventId, newRetryCount, event.maxRetries, nextRetryAt, e)
                    }
                    failCount++
                }
            }

            log.info("웹훅 재처리 완료. 성공: {}, 실패: {}", successCount, failCount)
        } finally {
            releaseAdvisoryLock(LOCK_WEBHOOK_RETRY)
        }
    }

    private fun tryAdvisoryLock(lockId: Long): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, lockId)
                stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }
    } catch (e: Exception) {
        log.warn("Advisory lock 획득 실패: lockId=$lockId", e)
        false
    }

    private fun releaseAdvisoryLock(lockId: Long) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                    stmt.setLong(1, lockId)
                    stmt.execute()
                }
            }
        } catch (e: Exception) {
            log.warn("Advisory lock 해제 실패: lockId=$lockId", e)
        }
    }
}
