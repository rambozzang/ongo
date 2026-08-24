package com.ongo.application.video

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 방치된 업로드 회수 스케줄. 실제 판단과 삭제는 StaleUploadCleanupUseCase 가 한다.
 */
@Component
class StaleUploadCleanupScheduler(
    private val staleUploadCleanupUseCase: StaleUploadCleanupUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${storage.upload.cleanup-cron:0 20 * * * *}") // 매시 20분
    fun cleanupStaleUploads() {
        try {
            val reclaimed = staleUploadCleanupUseCase.cleanupStaleUploads()
            if (reclaimed > 0) log.info("방치된 업로드 {}건 회수", reclaimed)
        } catch (e: Exception) {
            // 정리 실패가 다음 주기를 막으면 안 된다. 남은 대상은 다음 실행이 다시 걷는다.
            log.error("방치된 업로드 회수 실패: {}", e.message, e)
        }
    }
}
