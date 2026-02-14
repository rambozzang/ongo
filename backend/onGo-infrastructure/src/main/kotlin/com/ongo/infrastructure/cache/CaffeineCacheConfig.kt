package com.ongo.infrastructure.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CaffeineCacheConfig {

    companion object {
        const val DASHBOARD_KPI_CACHE = "dashboardKpi"
        const val CHANNEL_INFO_CACHE = "channelInfo"
        const val ANALYTICS_DATA_CACHE = "analyticsData"
        const val USER_SETTINGS_CACHE = "userSettings"
        const val TREND_DATA_CACHE = "trendData"
        const val REVENUE_SUMMARY_CACHE = "revenueSummary"
        const val REVENUE_TRENDS_CACHE = "revenueTrends"
        const val OPTIMAL_TIMES_CACHE = "optimalTimes"
        const val COHORT_ANALYSIS_CACHE = "cohortAnalysis"
        const val PERMISSIONS_CACHE = "permissions"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val caches = listOf(
            buildCache(DASHBOARD_KPI_CACHE, 5, TimeUnit.MINUTES, 500),
            buildCache(CHANNEL_INFO_CACHE, 1, TimeUnit.HOURS, 1000),
            buildCache(ANALYTICS_DATA_CACHE, 15, TimeUnit.MINUTES, 500),
            buildCache(USER_SETTINGS_CACHE, 30, TimeUnit.MINUTES, 1000),
            buildCache(TREND_DATA_CACHE, 10, TimeUnit.MINUTES, 500),
            buildCache(REVENUE_SUMMARY_CACHE, 10, TimeUnit.MINUTES, 500),
            buildCache(REVENUE_TRENDS_CACHE, 10, TimeUnit.MINUTES, 500),
            buildCache(OPTIMAL_TIMES_CACHE, 30, TimeUnit.MINUTES, 500),
            buildCache(COHORT_ANALYSIS_CACHE, 15, TimeUnit.MINUTES, 500),
            buildCache(PERMISSIONS_CACHE, 10, TimeUnit.MINUTES, 1000),
        )

        return SimpleCacheManager().apply {
            setCaches(caches)
        }
    }

    private fun buildCache(
        name: String,
        duration: Long,
        timeUnit: TimeUnit,
        maximumSize: Long,
    ): CaffeineCache =
        CaffeineCache(
            name,
            Caffeine.newBuilder()
                .expireAfterWrite(duration, timeUnit)
                .maximumSize(maximumSize)
                .recordStats()
                .build(),
        )
}
