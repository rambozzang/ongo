package com.ongo.domain.channel

import com.ongo.common.enums.Platform
import java.time.LocalDate

/**
 * Domain port interface for platform client operations.
 * Implemented by infrastructure layer (PlatformClientFactory adapter).
 */
interface PlatformClientPort {

    fun getVideoAnalytics(platform: Platform, platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalyticsResult

    fun getChannelInfo(platform: Platform, accessToken: String): PlatformChannelInfoResult

    fun refreshToken(platform: Platform, refreshToken: String): PlatformTokenRefreshResult
}

data class PlatformAnalyticsResult(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Int,
)

data class PlatformChannelInfoResult(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val subscriberCount: Long,
    val profileImageUrl: String?,
)

data class PlatformTokenRefreshResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
