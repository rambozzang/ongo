package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlatformAnalyticsResult
import com.ongo.domain.channel.PlatformChannelInfoResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformTokenRefreshResult
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PlatformClientPortAdapter(
    private val platformClientFactory: PlatformClientFactory,
) : PlatformClientPort {

    private val log = LoggerFactory.getLogger(javaClass)

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "getVideoAnalyticsFallback")
    override fun getVideoAnalytics(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): PlatformAnalyticsResult {
        val client = platformClientFactory.getClient(platform)
        val analytics = client.getVideoAnalytics(platformVideoId, accessToken, startDate, endDate)
        return PlatformAnalyticsResult(
            views = analytics.views,
            likes = analytics.likes,
            comments = analytics.comments,
            shares = analytics.shares,
            watchTimeSeconds = analytics.watchTimeSeconds,
            subscriberGained = analytics.subscriberGained,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "getChannelInfoFallback")
    override fun getChannelInfo(platform: Platform, accessToken: String): PlatformChannelInfoResult {
        val client = platformClientFactory.getClient(platform)
        val info = client.getChannelInfo(accessToken)
        return PlatformChannelInfoResult(
            channelId = info.channelId,
            channelName = info.channelName,
            channelUrl = info.channelUrl,
            subscriberCount = info.subscriberCount,
            profileImageUrl = info.profileImageUrl,
        )
    }

    @Retry(name = "platformApi")
    override fun refreshToken(platform: Platform, refreshToken: String): PlatformTokenRefreshResult {
        val client = platformClientFactory.getClient(platform)
        val result = client.refreshToken(refreshToken)
        return PlatformTokenRefreshResult(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
        )
    }

    @Suppress("unused")
    private fun getVideoAnalyticsFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
        e: Throwable,
    ): PlatformAnalyticsResult {
        log.warn("플랫폼 {} 분석 데이터 조회 실패 (Circuit Breaker): {}", platform, e.message)
        return PlatformAnalyticsResult(0, 0, 0, 0, 0, 0)
    }

    @Suppress("unused")
    private fun getChannelInfoFallback(
        platform: Platform,
        accessToken: String,
        e: Throwable,
    ): PlatformChannelInfoResult {
        log.warn("플랫폼 {} 채널 정보 조회 실패 (Circuit Breaker): {}", platform, e.message)
        throw e
    }
}
