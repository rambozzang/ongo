package com.ongo.infrastructure.external.platform

import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.ChannelLookupResult
import com.ongo.infrastructure.external.youtube.YouTubeApi
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ChannelLookupPortAdapter(
    private val youTubeApi: YouTubeApi,
    private val youTubeConfig: YouTubeConfig,
) : ChannelLookupPort {

    private val log = LoggerFactory.getLogger(ChannelLookupPortAdapter::class.java)

    override fun lookupChannel(platform: String, query: String): ChannelLookupResult {
        return when (platform.uppercase()) {
            "YOUTUBE" -> lookupYouTubeChannel(query)
            else -> ChannelLookupResult(
                found = false,
                platform = platform,
                requiresManualInput = true,
                message = "이 플랫폼은 자동 채널 조회를 지원하지 않습니다. 채널 정보를 직접 입력해주세요.",
            )
        }
    }

    private fun lookupYouTubeChannel(query: String): ChannelLookupResult {
        val apiKey = youTubeConfig.getApiKey()
        if (apiKey.isBlank()) {
            return ChannelLookupResult(
                found = false,
                platform = "YOUTUBE",
                requiresManualInput = true,
                message = "YouTube API 키가 설정되지 않았습니다. 채널 정보를 직접 입력해주세요.",
            )
        }

        val parsed = parseYouTubeQuery(query.trim())

        return try {
            val response = when (parsed) {
                is YouTubeQueryType.Handle -> youTubeApi.searchChannelByHandle(
                    part = "snippet,statistics",
                    forHandle = parsed.handle,
                    key = apiKey,
                )
                is YouTubeQueryType.ChannelId -> youTubeApi.searchChannelById(
                    part = "snippet,statistics",
                    id = parsed.channelId,
                    key = apiKey,
                )
            }

            val item = response.items.firstOrNull()
                ?: return ChannelLookupResult(
                    found = false,
                    platform = "YOUTUBE",
                    message = "채널을 찾을 수 없습니다. URL 또는 핸들을 확인해주세요.",
                )

            val customUrl = item.snippet?.customUrl
            val channelUrl = if (customUrl != null) {
                "https://youtube.com/$customUrl"
            } else {
                "https://youtube.com/channel/${item.id}"
            }

            ChannelLookupResult(
                found = true,
                platformChannelId = item.id,
                channelName = item.snippet?.title,
                channelUrl = channelUrl,
                subscriberCount = item.statistics?.subscriberCount?.toLongOrNull() ?: 0,
                totalViews = item.statistics?.viewCount?.toLongOrNull() ?: 0,
                videoCount = item.statistics?.videoCount?.toIntOrNull() ?: 0,
                profileImageUrl = item.snippet?.thumbnails?.default?.url,
                platform = "YOUTUBE",
            )
        } catch (e: Exception) {
            log.error("YouTube 채널 조회 실패: query={}, error={}", query, e.message, e)
            ChannelLookupResult(
                found = false,
                platform = "YOUTUBE",
                message = "YouTube 채널 조회 중 오류가 발생했습니다: ${e.message}",
            )
        }
    }

    private fun parseYouTubeQuery(query: String): YouTubeQueryType {
        // Pattern: https://youtube.com/@handle or https://www.youtube.com/@handle
        val handleUrlPattern = Regex("""(?:https?://)?(?:www\.)?youtube\.com/@([^/?&]+)""")
        handleUrlPattern.find(query)?.let {
            return YouTubeQueryType.Handle(it.groupValues[1])
        }

        // Pattern: https://youtube.com/channel/UCxxxx
        val channelUrlPattern = Regex("""(?:https?://)?(?:www\.)?youtube\.com/channel/([^/?&]+)""")
        channelUrlPattern.find(query)?.let {
            return YouTubeQueryType.ChannelId(it.groupValues[1])
        }

        // Raw handle: @handle
        if (query.startsWith("@")) {
            return YouTubeQueryType.Handle(query.removePrefix("@"))
        }

        // Raw channel ID: UCxxxx (starts with UC and is 24 chars)
        if (query.startsWith("UC") && query.length >= 20) {
            return YouTubeQueryType.ChannelId(query)
        }

        // Default: treat as handle
        return YouTubeQueryType.Handle(query)
    }

    private sealed class YouTubeQueryType {
        data class Handle(val handle: String) : YouTubeQueryType()
        data class ChannelId(val channelId: String) : YouTubeQueryType()
    }
}
