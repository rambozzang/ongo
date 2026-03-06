package com.ongo.application.auth

import com.ongo.domain.auth.RefreshTokenPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RefreshTokenCleanupScheduler(
    private val refreshTokenPort: RefreshTokenPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
    fun cleanupExpiredTokens() {
        log.info("만료된 Refresh Token 정리 시작")
        try {
            refreshTokenPort.deleteExpired()
            log.info("만료된 Refresh Token 정리 완료")
        } catch (e: Exception) {
            log.error("만료된 Refresh Token 정리 실패: {}", e.message, e)
        }
    }
}
