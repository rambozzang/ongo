package com.ongo.application.competitor

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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    fun syncCompetitorData() {
        log.info("경쟁자 데이터 동기화 시작")
        val competitors = competitorRepository.findAll()
        val today = LocalDate.now()

        competitors.forEach { competitor ->
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
        log.info("경쟁자 데이터 동기화 완료: {}건", competitors.size)
    }
}
