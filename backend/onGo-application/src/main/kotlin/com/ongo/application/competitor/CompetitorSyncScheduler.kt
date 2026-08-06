package com.ongo.application.competitor

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CompetitorSyncScheduler(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    fun syncCompetitorData() {
        log.info("경쟁자 데이터 동기화 시작")
        val competitors = competitorRepository.findAll()
        val today = LocalDate.now()

        // 동결된 계정은 건너뛴다. 스케줄러는 HTTP 필터를 지나지 않으므로 여기서 직접 본다.
        //
        // 사용자당 한 번만 조회한다. 경쟁 채널은 사용자별로 여러 개라 항목마다 물으면
        // 같은 답을 반복해서 받게 된다.
        //
        // 조회가 실패해도 건너뛴다. 가드가 fail-closed 라 예외를 던지고, 여기서 그걸
        // "쓰기 허용"으로 해석하면 삭제 진행 중인 계정에 데이터가 들어간다.
        // 이번 회차를 거르는 쪽이 낫다 — 다음 실행에서 다시 시도한다.
        val writable = HashMap<Long, Boolean>()
        fun canWrite(userId: Long): Boolean = writable.getOrPut(userId) {
            try {
                userWriteGuard.requireWritable(userId)
                true
            } catch (e: AccountFrozenException) {
                log.info("동결된 계정이라 경쟁자 동기화를 건너뛴다. userId={} 사유={}", userId, e.message)
                false
            }
        }

        var skipped = 0
        competitors.forEach { competitor ->
            if (!canWrite(competitor.userId)) {
                skipped++
                return@forEach
            }
            try {
                val result = channelLookupPort.lookupChannel(
                    platform = competitor.platform,
                    query = competitor.platformChannelId,
                )
                if (result.found) {
                    // Update competitor snapshot
                    competitorRepository.update(competitor.copy(
                        subscriberCount = result.subscriberCount,
                        totalViews = result.totalViews,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        lastSyncedAt = LocalDateTime.now(),
                    ))

                    // Save daily analytics
                    competitorRepository.upsertAnalytics(CompetitorAnalyticsDaily(
                        competitorId = competitor.id!!,
                        date = today,
                        subscriberCount = result.subscriberCount,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        totalViews = result.totalViews,
                    ))
                    log.debug("경쟁자 동기화 완료: {} ({})", competitor.channelName, competitor.platform)
                }
            } catch (e: Exception) {
                log.warn("경쟁자 동기화 실패 [{}]: {}", competitor.channelName, e.message)
            }
        }
        log.info("경쟁자 데이터 동기화 완료: {}건 (동결로 건너뜀 {}건)", competitors.size - skipped, skipped)
    }
}
