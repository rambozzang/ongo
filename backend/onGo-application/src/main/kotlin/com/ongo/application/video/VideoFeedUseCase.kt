package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class VideoFeedUseCase(
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(value = ["videoFeed"], key = "#userId + '-' + (#platform?.name() ?: 'ALL') + '-' + #page")
    fun getFeed(
        userId: Long,
        platform: Platform?,
        page: Int,
        size: Int,
        sort: String?,
    ): VideoFeedResponse {
        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == "ACTIVE" }
            .filter { platform == null || it.platform == platform }

        if (channels.isEmpty()) {
            return VideoFeedResponse(items = emptyList(), platforms = emptyList())
        }

        val allItems = mutableListOf<VideoFeedItem>()
        val platformErrors = mutableListOf<String>()

        for (channel in channels) {
            try {
                val result = platformClientPort.listVideos(
                    platform = channel.platform,
                    accessToken = channel.accessToken,
                    platformChannelId = channel.platformChannelId,
                    maxResults = size,
                    pageToken = if (page > 0) page.toString() else null,
                )
                result.items.forEach { item ->
                    allItems.add(
                        VideoFeedItem(
                            platformVideoId = item.platformVideoId,
                            platform = channel.platform,
                            channelName = channel.channelName,
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
                    )
                }
            } catch (e: Exception) {
                log.error("플랫폼 {} 피드 조회 실패: {}", channel.platform, e.message)
                platformErrors.add(channel.platform.name)
            }
        }

        val sorted = when (sort) {
            "views" -> allItems.sortedByDescending { it.viewCount }
            "likes" -> allItems.sortedByDescending { it.likeCount }
            "comments" -> allItems.sortedByDescending { it.commentCount }
            else -> allItems.sortedByDescending { it.publishedAt }
        }

        return VideoFeedResponse(
            items = sorted.take(size),
            platforms = channels.map { it.platform }.distinct(),
            errors = platformErrors.ifEmpty { null },
        )
    }
}

data class VideoFeedItem(
    val platformVideoId: String,
    val platform: Platform,
    val channelName: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)

data class VideoFeedResponse(
    val items: List<VideoFeedItem>,
    val platforms: List<Platform>,
    val errors: List<String>? = null,
)
