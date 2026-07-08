package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.domain.channel.PlatformAnalyticsResult
import com.ongo.domain.channel.PlatformChannelInfoResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformTokenRefreshResult
import com.ongo.domain.channel.PlatformFeedPortResult
import com.ongo.domain.channel.FeedItemResult
import com.ongo.domain.channel.PlatformVideoMetadataResult
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
            impressions = analytics.impressions,
            avgViewDurationSeconds = analytics.avgViewDurationSeconds,
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

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "deleteVideoFallback")
    override fun deleteVideo(platform: Platform, platformVideoId: String, accessToken: String): Boolean {
        val client = platformClientFactory.getClient(platform)
        return client.deleteVideo(platformVideoId, accessToken)
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "updateVideoMetadataFallback")
    override fun updateVideoMetadata(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        title: String,
        description: String,
        tags: List<String>,
    ): Boolean {
        val client = platformClientFactory.getClient(platform)
        return client.updateVideoMetadata(platformVideoId, accessToken, title, description, tags)
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "getVideoMetadataFallback")
    override fun getVideoMetadata(platform: Platform, platformVideoId: String, accessToken: String): PlatformVideoMetadataResult? {
        val client = platformClientFactory.getClient(platform)
        val meta = client.getVideoMetadata(platformVideoId, accessToken) ?: return null
        return PlatformVideoMetadataResult(
            title = meta.title,
            description = meta.description,
            tags = meta.tags,
            status = meta.status,
            viewCount = meta.viewCount,
            likeCount = meta.likeCount,
            commentCount = meta.commentCount,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "listVideosFallback")
    override fun listVideos(platform: Platform, accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedPortResult {
        val client = platformClientFactory.getClient(platform)
        val result = client.listVideos(accessToken, platformChannelId, maxResults, pageToken)
        return PlatformFeedPortResult(
            items = result.items.map { item ->
                FeedItemResult(
                    platformVideoId = item.platformVideoId,
                    title = item.title,
                    description = item.description,
                    thumbnailUrl = item.thumbnailUrl,
                    platformUrl = item.platformUrl,
                    viewCount = item.viewCount,
                    likeCount = item.likeCount,
                    commentCount = item.commentCount,
                    shareCount = item.shareCount,
                    publishedAt = item.publishedAt,
                )
            },
            nextPageToken = result.nextPageToken,
            totalCount = result.totalCount,
        )
    }

    @Suppress("unused")
    private fun getVideoMetadataFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        e: Throwable,
    ): PlatformVideoMetadataResult? {
        log.warn("플랫폼 {} 영상 메타데이터 조회 실패 (Circuit Breaker): {}", platform, e.message)
        return null
    }

    override fun revokeToken(platform: Platform, accessToken: String): Boolean {
        val client = platformClientFactory.getClient(platform)
        return client.revokeToken(accessToken)
    }

    @Suppress("unused")
    private fun listVideosFallback(
        platform: Platform,
        accessToken: String,
        platformChannelId: String?,
        maxResults: Int,
        pageToken: String?,
        e: Throwable,
    ): PlatformFeedPortResult {
        log.warn("플랫폼 {} 영상 목록 조회 실패 (Circuit Breaker): {}", platform, e.message)
        return PlatformFeedPortResult(emptyList())
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

    @Suppress("unused")
    private fun deleteVideoFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        e: Throwable,
    ): Boolean {
        log.warn("플랫폼 {} 영상 삭제 실패 (Circuit Breaker): {}", platform, e.message)
        throw PlatformApiException(platform.name, "영상 삭제 Circuit Breaker 발생: ${e.message}", e)
    }

    @Suppress("unused")
    private fun updateVideoMetadataFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        title: String,
        description: String,
        tags: List<String>,
        e: Throwable,
    ): Boolean {
        log.warn("플랫폼 {} 영상 메타데이터 업데이트 실패 (Circuit Breaker): {}", platform, e.message)
        throw PlatformApiException(platform.name, "영상 메타데이터 업데이트 Circuit Breaker 발생: ${e.message}", e)
    }
}
