package com.ongo.application.trend

import com.ongo.domain.trend.TrendRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TrendSyncScheduler(
    private val trendRepository: TrendRepository,
    private val trendDataSource: TrendDataSource,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *")
    fun syncDailyTrends() {
        log.info("트렌드 일일 동기화 시작")
        try {
            val trends = trendDataSource.fetchDailyTrends("KR")
            if (trends.isNotEmpty()) {
                trendRepository.saveBatch(trends)
                log.info("트렌드 수집 완료: {}건", trends.size)
            }
        } catch (e: Exception) {
            log.error("트렌드 동기화 실패", e)
        }
    }
}
