package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlatformAnalyticsResult
import com.ongo.domain.channel.PlatformChannelInfoResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformTokenRefreshResult
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PlatformClientPortAdapter(
    private val platformClientFactory: PlatformClientFactory,
) : PlatformClientPort {

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

    override fun refreshToken(platform: Platform, refreshToken: String): PlatformTokenRefreshResult {
        val client = platformClientFactory.getClient(platform)
        val result = client.refreshToken(refreshToken)
        return PlatformTokenRefreshResult(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
        )
    }
}
